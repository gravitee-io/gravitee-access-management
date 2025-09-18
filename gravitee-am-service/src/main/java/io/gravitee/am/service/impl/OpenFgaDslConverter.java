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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimalny parser DSL OpenFGA => Authorization Model (schema 1.1)
 * Obsługuje:
 *  - "model schema 1.1"
 *  - "type <name>"
 *  - "relations" blok
 *  - "define <relation>: [type, type#relation, ...]"
 *
 * Relacje mapowane do:
 *  "relations": { "<rel>": { "this": {} } }
 *  "metadata.relations.<rel>.directly_related_user_types": [{ "type": "...", "relation": "..."? }]
 */
public final class OpenFgaDslConverter {

    private static final Pattern TYPE_LINE = Pattern.compile("^\\s*type\\s+([a-zA-Z0-9_:-]+)\\s*$");
    private static final Pattern DEFINE_LINE = Pattern.compile("^\\s*define\\s+([a-zA-Z0-9_:-]+)\\s*:\\s*\\[(.*)]\\s*$");
    private static final Pattern MODEL_SCHEMA = Pattern.compile("^\\s*model\\s+schema\\s+1\\.1\\s*$", Pattern.CASE_INSENSITIVE);

    private OpenFgaDslConverter() {}

    public static ObjectNode toAuthorizationModelJson(String dsl, ObjectMapper mapper) {
        if (mapper == null) mapper = new ObjectMapper();

        String[] lines = normalize(dsl).split("\\r?\\n");

        ObjectNode root = mapper.createObjectNode();
        root.put("schema_version", "1.1");
        ArrayNode typeDefs = mapper.createArrayNode();
        root.set("type_definitions", typeDefs);

        ObjectNode currentType = null;
        boolean inRelations = false;

        for (String raw : lines) {
            String line = stripComments(raw).trim();
            if (line.isEmpty()) continue;

            if (MODEL_SCHEMA.matcher(line).matches()) {
                // tylko walidujemy, schema zapisujemy powyżej
                continue;
            }

            Matcher typeM = TYPE_LINE.matcher(line);
            if (typeM.matches()) {
                // zamknij poprzedni typ
                if (currentType != null) {
                    typeDefs.add(currentType);
                }
                currentType = mapper.createObjectNode();
                currentType.put("type", typeM.group(1));
                inRelations = false;
                continue;
            }

            // wejście do bloku relations
            if (line.equalsIgnoreCase("relations")) {
                ensureCurrentType(currentType);
                inRelations = true;
                ensureRelationsNodes(mapper, currentType);
                continue;
            }

            if (inRelations) {
                // define <rel>: [a, b#c]
                Matcher defM = DEFINE_LINE.matcher(line);
                if (!defM.matches()) {
                    throw new IllegalArgumentException("Niepoprawna linia w 'relations': " + line);
                }
                String relName = defM.group(1);
                String subjectsBody = defM.group(2).trim();

                List<Subject> subjects = parseSubjects(subjectsBody);

                // relations.<rel> = { "this": {} }
                ObjectNode relationsNode = (ObjectNode) currentType.get("relations");
                ObjectNode relNode = mapper.createObjectNode();
                relNode.set("this", mapper.createObjectNode()); // semantyka: direct assignment
                relationsNode.set(relName, relNode);

                // metadata.relations.<rel>.directly_related_user_types = [...]
                ObjectNode metadataNode = (ObjectNode) currentType.get("metadata");
                ObjectNode mdRelations = (ObjectNode) metadataNode.get("relations");
                ObjectNode mdThisRel = mapper.createObjectNode();
                ArrayNode drut = mapper.createArrayNode();

                for (Subject s : subjects) {
                    ObjectNode subj = mapper.createObjectNode();
                    subj.put("type", s.type);
                    if (s.relation != null) subj.put("relation", s.relation);
                    drut.add(subj);
                }
                mdThisRel.set("directly_related_user_types", drut);
                mdRelations.set(relName, mdThisRel);

                continue;
            }

            // pusta linia / inny blok — ignorujemy
        }

        // domknij ostatni typ
        if (currentType != null) {
            typeDefs.add(currentType);
        }

        if (typeDefs.isEmpty()) {
            throw new IllegalArgumentException("Brak zdefiniowanych typów w DSL.");
        }

        return root;
    }

    // ---------------- helpers ----------------

    private static void ensureCurrentType(ObjectNode currentType) {
        if (currentType == null) {
            throw new IllegalStateException("Linia 'relations' wystąpiła przed 'type'.");
        }
    }

    private static void ensureRelationsNodes(ObjectMapper mapper, ObjectNode currentType) {
        if (!currentType.has("relations")) {
            currentType.set("relations", mapper.createObjectNode());
        }
        if (!currentType.has("metadata")) {
            ObjectNode md = mapper.createObjectNode();
            md.set("relations", mapper.createObjectNode());
            currentType.set("metadata", md);
        } else if (!currentType.get("metadata").has("relations")) {
            ((ObjectNode) currentType.get("metadata")).set("relations", mapper.createObjectNode());
        }
    }

    private static String stripComments(String s) {
        // opcjonalnie usuwamy komentarze w stylu "# ..."
        int idx = s.indexOf('#');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n");
    }

    private static List<Subject> parseSubjects(String body) {
        if (body.isBlank()) return Collections.emptyList();
        String[] parts = body.split(",");
        List<Subject> out = new ArrayList<>();
        for (String p : parts) {
            String token = p.trim();
            if (token.isEmpty()) continue;

            // dopuszczamy "type" lub "type#relation"
            int hash = token.indexOf('#');
            if (hash < 0) {
                out.add(new Subject(token, null));
            } else {
                String type = token.substring(0, hash).trim();
                String rel = token.substring(hash + 1).trim();
                if (type.isEmpty() || rel.isEmpty())
                    throw new IllegalArgumentException("Niepoprawny subject: " + token);
                out.add(new Subject(type, rel));
            }
        }
        return out;
    }

    private static final class Subject {
        final String type;
        final String relation;
        Subject(String type, String relation) {
            this.type = type;
            this.relation = relation;
        }
    }
}
