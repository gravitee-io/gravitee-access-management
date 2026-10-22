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

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rejects the {@code $ref}s and {@code $id}s of a Draft 7 schema that would make validation ignore keywords or
 * recurse without end. Expects a schema that satisfies the meta-schema; only subschemas are inspected, so
 * {@code const}, {@code enum}, {@code default} and unknown keywords are data.
 *
 * @author GraviteeSource Team
 */
final class SchemaReferences {

    private static final Set<String> SINGLE_SCHEMA_KEYWORDS = Set.of(
            "additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else");
    private static final Set<String> SCHEMA_ARRAY_KEYWORDS = Set.of("allOf", "anyOf", "oneOf");
    private static final Set<String> SCHEMA_MAP_KEYWORDS = Set.of("properties", "patternProperties", "definitions", "dependencies");
    // applied to the value being validated itself
    private static final Set<String> IN_PLACE_SINGLE_KEYWORDS = Set.of("not", "if", "then", "else");
    private static final Set<String> REF_COMPANIONS = Set.of("$ref", "$comment");

    private static final String ROOT = "#";

    private SchemaReferences() {
    }

    static Optional<String> check(JsonNode root) {
        Map<String, JsonNode> subschemas = new LinkedHashMap<>();
        collect(ROOT, root, subschemas);

        Map<String, List<String>> inPlace = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : subschemas.entrySet()) {
            String pointer = entry.getKey();
            JsonNode schema = entry.getValue();
            if (!schema.isObject()) {
                continue;
            }
            if (!ROOT.equals(pointer) && schema.has("$id")) {
                return Optional.of("Schema must not declare \"$id\" below its root, found at " + pointer);
            }
            List<String> targets = new ArrayList<>();
            JsonNode ref = schema.get("$ref");
            if (ref != null) {
                String value = ref.asText();
                if (!value.startsWith(ROOT)) {
                    return Optional.of("Schema must only contain local references (starting with \"#\"), found \"" + value + "\"");
                }
                Iterator<String> keywords = schema.fieldNames();
                while (keywords.hasNext()) {
                    String keyword = keywords.next();
                    if (!REF_COMPANIONS.contains(keyword)) {
                        return Optional.of("Schema must not combine \"$ref\" with other keywords, which Draft 7 ignores, found \""
                                + keyword + "\" at " + pointer);
                    }
                }
                Optional<String> target = resolve(value).filter(subschemas::containsKey);
                if (target.isEmpty()) {
                    return Optional.of("Schema reference \"" + value + "\" does not resolve to a subschema");
                }
                targets.add(target.get());
            }
            for (String keyword : SCHEMA_ARRAY_KEYWORDS) {
                JsonNode array = schema.get(keyword);
                if (array != null && array.isArray()) {
                    for (int i = 0; i < array.size(); i++) {
                        targets.add(child(child(pointer, keyword), String.valueOf(i)));
                    }
                }
            }
            for (String keyword : IN_PLACE_SINGLE_KEYWORDS) {
                if (schema.has(keyword)) {
                    targets.add(child(pointer, keyword));
                }
            }
            JsonNode dependencies = schema.get("dependencies");
            if (dependencies != null && dependencies.isObject()) {
                dependencies.fields().forEachRemaining(dependency -> {
                    if (!dependency.getValue().isArray()) {
                        targets.add(child(child(pointer, "dependencies"), dependency.getKey()));
                    }
                });
            }
            inPlace.put(pointer, targets);
        }
        return findCycle(subschemas.keySet(), inPlace)
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
            } else if ("items".equals(keyword)) {
                if (value.isArray()) {
                    for (int i = 0; i < value.size(); i++) {
                        collect(child(path, String.valueOf(i)), value.get(i), subschemas);
                    }
                } else {
                    collect(path, value, subschemas);
                }
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
        String fragment = URLDecoder.decode(ref.substring(1).replace("+", "%2B"), StandardCharsets.UTF_8);
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

    /**
     * A subschema on a cycle of in-place applications, or empty.
     */
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
