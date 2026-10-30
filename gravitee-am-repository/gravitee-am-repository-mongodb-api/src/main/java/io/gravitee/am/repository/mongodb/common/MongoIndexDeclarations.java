/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author GraviteeSource Team
 */
public final class MongoIndexDeclarations {

    public record CollectionIndexes(List<IndexModel> indexes, List<String> dropped) {
    }

    public record Changeset(String id, String checksum, Map<String, CollectionIndexes> indexesByCollection) {
    }

    private static final String ID_INDEX = "_id_";
    private static final int CHECKSUM_LENGTH = 12;
    private static final Set<String> INDEX_FIELDS = Set.of("key", "name", "unique", "sparse", "expireAfterSeconds", "expireAfter", "partialFilterExpression", "collation");
    private static final Set<String> CHANGESET_FIELDS = Set.of("id", "create", "drop");
    private static final Set<String> COLLATION_FIELDS = Set.of("locale", "strength");
    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhd])$");

    private final String source;
    private final Map<String, CollectionIndexes> indexesByCollection;
    private final List<Changeset> changesets;

    private MongoIndexDeclarations(String source, Map<String, CollectionIndexes> indexesByCollection, List<Changeset> changesets) {
        this.source = source;
        this.indexesByCollection = indexesByCollection;
        this.changesets = changesets;
    }

    public static MongoIndexDeclarations load(ClassLoader classLoader, String resource) {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Mongo index declarations not found: " + resource);
            }
            return parse(resource, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read Mongo index declarations " + resource, e);
        }
    }

    public static MongoIndexDeclarations parse(String source, String json) {
        try {
            Document root = Document.parse(json);
            requireOnly(root.keySet(), Set.of("changesets"), "root");
            List<?> declared = root.containsKey("changesets") ? asList(root.get("changesets"), "'changesets'") : List.of();
            if (declared.isEmpty()) {
                throw new IllegalArgumentException("'changesets' is missing or empty");
            }
            Replay replay = new Replay();
            List<Changeset> changesets = new ArrayList<>();
            for (Object value : declared) {
                changesets.add(replay.apply(asDocument(value, "a changeset")));
            }
            return new MongoIndexDeclarations(source, replay.collections(), List.copyOf(changesets));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Mongo index declarations " + source + ": " + e.getMessage(), e);
        }
    }

    public String source() {
        return source;
    }

    public Map<String, CollectionIndexes> indexesByCollection() {
        return indexesByCollection;
    }

    public List<Changeset> changesets() {
        return changesets;
    }

    private static final class Replay {

        private final Set<String> ids = new HashSet<>();
        private final Map<String, Map<String, IndexModel>> live = new TreeMap<>();
        private final Map<String, Set<String>> dropped = new TreeMap<>();

        private Changeset apply(Document changeset) {
            Object rawId = changeset.get("id");
            if (!(rawId instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("a changeset has no id");
            }
            requireOnly(changeset.keySet(), CHANGESET_FIELDS, "changeset " + id);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("changeset " + id + " is declared twice");
            }

            Document canonicalCreate = new Document();
            Map<String, List<IndexModel>> created = new TreeMap<>();
            new TreeMap<>(section(changeset, "create", id)).forEach((collection, value) ->
                    created.put(collection, parseCreated(id, collection, asList(value, "changeset " + id + " create." + collection), canonicalCreate)));

            Document canonicalDrop = new Document();
            Map<String, List<String>> drops = new TreeMap<>();
            new TreeMap<>(section(changeset, "drop", id)).forEach((collection, value) -> {
                List<String> names = parseDropped(id, collection, asList(value, "changeset " + id + " drop." + collection));
                drops.put(collection, names);
                canonicalDrop.append(collection, names);
            });

            if (created.values().stream().allMatch(List::isEmpty) && drops.values().stream().allMatch(List::isEmpty)) {
                throw new IllegalArgumentException("changeset " + id + " neither creates nor drops an index");
            }

            drops.forEach((collection, names) -> names.forEach(name -> {
                if (created.getOrDefault(collection, List.of()).stream().anyMatch(model -> name.equals(model.getOptions().getName()))) {
                    throw new IllegalArgumentException("changeset " + id + " both creates and drops index " + name + " of " + collection);
                }
                live.computeIfAbsent(collection, c -> new LinkedHashMap<>()).remove(name);
                dropped.computeIfAbsent(collection, c -> new TreeSet<>()).add(name);
            }));

            created.forEach((collection, models) -> models.forEach(model -> {
                String name = model.getOptions().getName();
                Map<String, IndexModel> indexes = live.computeIfAbsent(collection, c -> new LinkedHashMap<>());
                if (indexes.containsKey(name)) {
                    throw new IllegalArgumentException("changeset " + id + " creates index " + name + " of " + collection
                            + ", which an earlier changeset already created without dropping it");
                }
                indexes.put(name, model);
                dropped.getOrDefault(collection, new TreeSet<>()).remove(name);
            }));

            Set<String> collections = new TreeSet<>(created.keySet());
            collections.addAll(drops.keySet());
            Map<String, CollectionIndexes> delta = new LinkedHashMap<>();
            for (String collection : collections) {
                List<IndexModel> indexes = created.getOrDefault(collection, List.of());
                List<String> retired = drops.getOrDefault(collection, List.of());
                if (!indexes.isEmpty() || !retired.isEmpty()) {
                    delta.put(collection, new CollectionIndexes(indexes, retired));
                }
            }

            Document canonical = new Document("id", id).append("create", canonicalCreate).append("drop", canonicalDrop);
            return new Changeset(id, hash(canonical.toJson()), Map.copyOf(delta));
        }

        private Map<String, CollectionIndexes> collections() {
            Set<String> names = new TreeSet<>(live.keySet());
            names.addAll(dropped.keySet());
            Map<String, CollectionIndexes> collections = new LinkedHashMap<>();
            for (String name : names) {
                List<IndexModel> indexes = List.copyOf(live.getOrDefault(name, Map.of()).values());
                List<String> retired = List.copyOf(dropped.getOrDefault(name, Set.of()));
                if (!indexes.isEmpty() || !retired.isEmpty()) {
                    collections.put(name, new CollectionIndexes(indexes, retired));
                }
            }
            return Map.copyOf(collections);
        }
    }

    private static Document section(Document changeset, String field, String id) {
        Object value = changeset.get(field);
        return value == null ? new Document() : asDocument(value, "changeset " + id + " " + field);
    }

    private static List<IndexModel> parseCreated(String id, String collection, List<?> declared, Document canonical) {
        List<Document> canonicalIndexes = new ArrayList<>();
        List<IndexModel> indexes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object value : declared) {
            Document index = asDocument(value, collection);
            requireOnly(index.keySet(), INDEX_FIELDS, collection);
            Document canonicalIndex = new Document();
            IndexModel model = parseIndex(collection, index, canonicalIndex);
            if (!names.add(model.getOptions().getName())) {
                throw new IllegalArgumentException("changeset " + id + " creates index " + model.getOptions().getName() + " of " + collection + " twice");
            }
            indexes.add(model);
            canonicalIndexes.add(canonicalIndex);
        }
        canonicalIndexes.sort(Comparator.comparing(index -> index.getString("name")));
        canonical.append(collection, canonicalIndexes);
        return List.copyOf(indexes);
    }

    private static List<String> parseDropped(String id, String collection, List<?> declared) {
        Set<String> names = new TreeSet<>();
        for (Object value : declared) {
            if (!(value instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("changeset " + id + " drops an index of " + collection + " without a name");
            }
            if (ID_INDEX.equals(name)) {
                throw new IllegalArgumentException("the id index of " + collection + " cannot be dropped");
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static IndexModel parseIndex(String collection, Document index, Document canonical) {
        String name = index.getString("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("an index of " + collection + " has no name");
        }
        Document key = index.get("key", Document.class);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("index " + name + " of " + collection + " has no key");
        }

        IndexOptions options = new IndexOptions().name(name).background(true);
        canonical.append("name", name).append("key", key);

        if (Boolean.TRUE.equals(index.getBoolean("unique"))) {
            options.unique(true);
            canonical.append("unique", true);
        }
        if (Boolean.TRUE.equals(index.getBoolean("sparse"))) {
            options.sparse(true);
            canonical.append("sparse", true);
        }

        Long expireAfterSeconds = expireAfterSeconds(collection, name, index);
        if (expireAfterSeconds != null) {
            options.expireAfter(expireAfterSeconds, TimeUnit.SECONDS);
            canonical.append("expireAfterSeconds", expireAfterSeconds);
        }

        Document partialFilter = index.get("partialFilterExpression", Document.class);
        if (partialFilter != null) {
            options.partialFilterExpression(partialFilter);
            canonical.append("partialFilterExpression", partialFilter);
        }

        Document collation = index.get("collation", Document.class);
        if (collation != null) {
            requireOnly(collation.keySet(), COLLATION_FIELDS, collection + "." + name + ".collation");
            if (!(collation.get("locale") instanceof String locale) || locale.isBlank()) {
                throw new IllegalArgumentException("collation of " + collection + "." + name + " has no locale");
            }
            Collation.Builder builder = Collation.builder().locale(locale);
            Integer strength = collation.getInteger("strength");
            if (strength != null) {
                builder.collationStrength(CollationStrength.fromInt(strength));
            }
            options.collation(builder.build());
            canonical.append("collation", collation);
        }

        return new IndexModel(key, options);
    }

    private static Long expireAfterSeconds(String collection, String name, Document index) {
        Object seconds = index.get("expireAfterSeconds");
        Object duration = index.get("expireAfter");
        if (seconds != null && duration != null) {
            throw new IllegalArgumentException("index " + name + " of " + collection + " declares both expireAfterSeconds and expireAfter");
        }
        if (seconds != null) {
            if (!(seconds instanceof Number number)) {
                throw new IllegalArgumentException("expireAfterSeconds of " + collection + "." + name + " is not a number");
            }
            return number.longValue();
        }
        if (duration != null) {
            Matcher matcher = DURATION.matcher(String.valueOf(duration));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("expireAfter of " + collection + "." + name + " is not a duration like 90d: " + duration);
            }
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "m" -> TimeUnit.MINUTES.toSeconds(amount);
                case "h" -> TimeUnit.HOURS.toSeconds(amount);
                case "d" -> TimeUnit.DAYS.toSeconds(amount);
                default -> amount;
            };
        }
        return null;
    }

    private static Document asDocument(Object value, String context) {
        if (value instanceof Document document) {
            return document;
        }
        throw new IllegalArgumentException(context + " must be an object");
    }

    private static List<?> asList(Object value, String context) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException(context + " must be an array");
    }

    private static void requireOnly(Set<String> fields, Set<String> allowed, String context) {
        fields.stream()
                .filter(field -> !allowed.contains(field))
                .findFirst()
                .ifPresent(field -> {
                    throw new IllegalArgumentException("unknown field '" + field + "' in " + context);
                });
    }

    private static String hash(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, CHECKSUM_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
