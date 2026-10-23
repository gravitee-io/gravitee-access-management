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
package io.gravitee.am.service.validators.authorizationdetailtype;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.SchemaLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.model.oauth2.AuthorizationDetailType.SCHEMA_MAX_LENGTH;

/**
 * @author GraviteeSource Team
 */
final class AuthorizationDetailTypeSchemaValidator {

    private static final String DIALECT_BASE = "https://json-schema.org/draft/2020-12/";
    private static final String META_SCHEMA_ID = "urn:gravitee:am:authorization-detail-type:meta-schema";
    private static final String META_SCHEMA_RESOURCE = "/authorization-detail-type/meta-schema.json";
    private static final int MAX_REPORTED_ERRORS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder
                    .schemaLoader(SchemaLoader.getDefault())
                    .schemas(Map.of(META_SCHEMA_ID, readResource(META_SCHEMA_RESOURCE)))
                    .schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .locale(Locale.ENGLISH)
                            .errorMessageKeyword("message")
                            .build()));

    private static final Schema META_SCHEMA = SCHEMA_REGISTRY.getSchema(SchemaLocation.of(META_SCHEMA_ID));

    Optional<String> validate(String type, String schema) {
        if (schema.length() > SCHEMA_MAX_LENGTH) {
            return Optional.of("Schema must be at most " + SCHEMA_MAX_LENGTH + " characters");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(schema);
        } catch (JsonProcessingException e) {
            return Optional.of("Schema is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return Optional.of("Schema must be a JSON object");
        }
        Map<Boolean, List<Error>> errors = META_SCHEMA.validate(root).stream()
                .collect(Collectors.partitioningBy(AuthorizationDetailTypeSchemaValidator::isDialectError));
        if (!errors.get(true).isEmpty()) {
            return Optional.of("Schema is not a valid JSON Schema 2020-12: " + errors.get(true).stream()
                    .map(Error::toString)
                    .distinct()
                    .limit(MAX_REPORTED_ERRORS)
                    .collect(Collectors.joining(", ")));
        }
        return errors.get(false).stream()
                .findFirst()
                .map(error -> error.getMessage() + ", found at #" + error.getInstanceLocation())
                .or(() -> References.check(root))
                .or(() -> validatePinnedType(root, type));
    }

    private static boolean isDialectError(Error error) {
        return error.getSchemaLocation().getAbsoluteIri().toString().startsWith(DIALECT_BASE);
    }

    private static Optional<String> validatePinnedType(JsonNode root, String type) {
        if (type.equals(root.path("properties").path("type").path("const").asText(null))) {
            return Optional.empty();
        }
        return Optional.of("Schema must constrain \"type\" to \"" + type + "\": properties.type.const must equal the type");
    }

    private static String readResource(String name) {
        try (InputStream resource = AuthorizationDetailTypeSchemaValidator.class.getResourceAsStream(name)) {
            if (resource == null) {
                throw new IllegalStateException("Missing resource " + name);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Expects a schema that satisfies the meta-schema, so every {@code $ref} is local and no {@code $id} or anchor moves it.
     */
    private static final class References {

        private static final Set<String> SINGLE_SCHEMA_KEYWORDS = Set.of(
                "additionalProperties", "items", "contains", "propertyNames", "not", "if", "then", "else",
                "unevaluatedItems", "unevaluatedProperties", "contentSchema");
        private static final Set<String> SCHEMA_ARRAY_KEYWORDS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
        private static final Set<String> SCHEMA_MAP_KEYWORDS = Set.of("properties", "patternProperties", "$defs", "dependentSchemas");
        private static final Set<String> SAME_VALUE_ARRAY_KEYWORDS = Set.of("allOf", "anyOf", "oneOf");
        private static final Set<String> SAME_VALUE_SINGLE_KEYWORDS = Set.of("not", "if", "then", "else");

        private static final String ROOT = "#";

        private References() {
        }

        static Optional<String> check(JsonNode root) {
            Map<String, JsonNode> subschemas = new LinkedHashMap<>();
            collect(ROOT, root, subschemas);

            Map<String, List<String>> sameValue = new HashMap<>();
            for (Map.Entry<String, JsonNode> entry : subschemas.entrySet()) {
                String pointer = entry.getKey();
                JsonNode schema = entry.getValue();
                if (!schema.isObject()) {
                    continue;
                }
                List<String> targets = new ArrayList<>();
                JsonNode ref = schema.get("$ref");
                if (ref != null) {
                    String value = ref.asText();
                    Optional<String> target = resolve(value).filter(subschemas::containsKey);
                    if (target.isEmpty()) {
                        return Optional.of("Schema reference \"" + value + "\" does not resolve to a subschema");
                    }
                    targets.add(target.get());
                }
                for (String keyword : SAME_VALUE_ARRAY_KEYWORDS) {
                    JsonNode array = schema.get(keyword);
                    if (array != null && array.isArray()) {
                        for (int i = 0; i < array.size(); i++) {
                            targets.add(child(child(pointer, keyword), String.valueOf(i)));
                        }
                    }
                }
                for (String keyword : SAME_VALUE_SINGLE_KEYWORDS) {
                    if (schema.has(keyword)) {
                        targets.add(child(pointer, keyword));
                    }
                }
                JsonNode dependentSchemas = schema.get("dependentSchemas");
                if (dependentSchemas != null && dependentSchemas.isObject()) {
                    dependentSchemas.fieldNames().forEachRemaining(property -> targets.add(child(child(pointer, "dependentSchemas"), property)));
                }
                sameValue.put(pointer, targets);
            }
            return findCycle(subschemas.keySet(), sameValue)
                    .map(pointer -> "Schema must not apply itself to the same value without end, through " + pointer);
        }

        private static void collect(String pointer, JsonNode schema, Map<String, JsonNode> subschemas) {
            if (!schema.isObject() && !schema.isBoolean()) {
                return;
            }
            subschemas.put(pointer, schema);
            if (!schema.isObject()) {
                return;
            }
            schema.fields().forEachRemaining(member -> {
                String keyword = member.getKey();
                JsonNode value = member.getValue();
                String path = child(pointer, keyword);
                if (SINGLE_SCHEMA_KEYWORDS.contains(keyword)) {
                    collect(path, value, subschemas);
                } else if (SCHEMA_ARRAY_KEYWORDS.contains(keyword) && value.isArray()) {
                    for (int i = 0; i < value.size(); i++) {
                        collect(child(path, String.valueOf(i)), value.get(i), subschemas);
                    }
                } else if (SCHEMA_MAP_KEYWORDS.contains(keyword) && value.isObject()) {
                    value.fields().forEachRemaining(entry -> collect(child(path, entry.getKey()), entry.getValue(), subschemas));
                }
            });
        }

        /**
         * The canonical pointer of a {@code #...} reference, or empty when its fragment is not a JSON pointer.
         */
        private static Optional<String> resolve(String ref) {
            String fragment;
            try {
                fragment = URLDecoder.decode(ref.substring(1).replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            if (fragment.isEmpty()) {
                return Optional.of(ROOT);
            }
            if (!fragment.startsWith("/")) {
                return Optional.empty();
            }
            String pointer = ROOT;
            for (String token : fragment.substring(1).split("/", -1)) {
                pointer = child(pointer, token.replace("~1", "/").replace("~0", "~"));
            }
            return Optional.of(pointer);
        }

        private static String child(String pointer, String token) {
            return pointer + "/" + token.replace("~", "~0").replace("/", "~1");
        }

        private static Optional<String> findCycle(Set<String> nodes, Map<String, List<String>> edges) {
            Map<String, Boolean> onPath = new HashMap<>();
            for (String start : nodes) {
                if (onPath.containsKey(start)) {
                    continue;
                }
                Deque<Iterator<String>> stack = new ArrayDeque<>();
                Deque<String> path = new ArrayDeque<>();
                onPath.put(start, true);
                path.push(start);
                stack.push(edges.getOrDefault(start, List.of()).iterator());
                while (!stack.isEmpty()) {
                    Iterator<String> next = stack.peek();
                    if (next.hasNext()) {
                        String target = next.next();
                        Boolean visiting = onPath.get(target);
                        if (Boolean.TRUE.equals(visiting)) {
                            return Optional.of(target);
                        }
                        if (visiting == null) {
                            onPath.put(target, true);
                            path.push(target);
                            stack.push(edges.getOrDefault(target, List.of()).iterator());
                        }
                    } else {
                        stack.pop();
                        onPath.put(path.pop(), false);
                    }
                }
            }
            return Optional.empty();
        }
    }
}
