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
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author GraviteeSource Team
 */
public final class MongoIndexDeclarations {

    public record CollectionIndexes(List<IndexModel> indexes, List<String> dropped) {
    }

    private static final String ID_INDEX = "_id_";
    private static final int CHECKSUM_LENGTH = 12;
    private static final Set<String> INDEX_FIELDS = Set.of("key", "name", "unique", "sparse", "expireAfterSeconds", "expireAfter", "partialFilterExpression", "collation");
    private static final Set<String> COLLECTION_FIELDS = Set.of("indexes", "dropped");
    private static final Set<String> COLLATION_FIELDS = Set.of("locale", "strength");
    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhd])$");

    private final String source;
    private final Map<String, CollectionIndexes> collections;
    private final String checksum;

    private MongoIndexDeclarations(String source, Map<String, CollectionIndexes> collections, String checksum) {
        this.source = source;
        this.collections = collections;
        this.checksum = checksum;
    }

    public static MongoIndexDeclarations load(ClassLoader classLoader, String resource, UnaryOperator<String> placeholderResolver) {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Mongo index declarations not found: " + resource);
            }
            return parse(resource, placeholderResolver.apply(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read Mongo index declarations " + resource, e);
        }
    }

    public static MongoIndexDeclarations parse(String source, String json) {
        try {
            Document root = Document.parse(json);
            requireOnly(root.keySet(), Set.of("collections"), "root");
            Document declared = root.get("collections", Document.class);
            if (declared == null) {
                throw new IllegalArgumentException("'collections' is missing");
            }
            Map<String, CollectionIndexes> collections = new LinkedHashMap<>();
            Document canonical = new Document();
            new TreeMap<>(declared).forEach((name, value) -> {
                Document collection = asDocument(value, name);
                requireOnly(collection.keySet(), COLLECTION_FIELDS, name);
                collections.put(name, parseCollection(name, collection, canonical));
            });
            return new MongoIndexDeclarations(source, Map.copyOf(collections), hash(canonical.toJson()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Mongo index declarations " + source + ": " + e.getMessage(), e);
        }
    }

    public String source() {
        return source;
    }

    public Map<String, CollectionIndexes> collections() {
        return collections;
    }

    public String checksum() {
        return checksum;
    }

    private static CollectionIndexes parseCollection(String collection, Document declaration, Document canonical) {
        List<Document> canonicalIndexes = new ArrayList<>();
        List<IndexModel> indexes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object value : declaration.getList("indexes", Object.class, List.of())) {
            Document index = asDocument(value, collection);
            requireOnly(index.keySet(), INDEX_FIELDS, collection);
            Document canonicalIndex = new Document();
            IndexModel model = parseIndex(collection, index, canonicalIndex);
            if (!names.add(model.getOptions().getName())) {
                throw new IllegalArgumentException("index " + model.getOptions().getName() + " of " + collection + " is declared twice");
            }
            indexes.add(model);
            canonicalIndexes.add(canonicalIndex);
        }

        List<String> dropped = declaration.getList("dropped", String.class, List.of()).stream().distinct().sorted().toList();
        for (String name : dropped) {
            if (ID_INDEX.equals(name)) {
                throw new IllegalArgumentException("the id index of " + collection + " cannot be dropped");
            }
            if (names.contains(name)) {
                throw new IllegalArgumentException("index " + name + " of " + collection + " is both declared and dropped");
            }
        }

        canonicalIndexes.sort(Comparator.comparing(index -> index.getString("name")));
        canonical.append(collection, new Document("indexes", canonicalIndexes).append("dropped", dropped));
        return new CollectionIndexes(List.copyOf(indexes), dropped);
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
            Collation.Builder builder = Collation.builder().locale(collation.getString("locale"));
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
