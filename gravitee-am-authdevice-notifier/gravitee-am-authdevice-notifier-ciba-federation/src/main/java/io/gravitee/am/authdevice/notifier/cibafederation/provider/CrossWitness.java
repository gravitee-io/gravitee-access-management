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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class CrossWitness {

    private CrossWitness() {}

    /**
     * True iff {@code expectedHash} equals the canonical SHA-256 of {@code value}. Used to compare a
     * stored pre-send hash against a freshly-received payload (the cross-witness).
     */
    public static boolean matchesHash(String expectedHash, Object value) {
        return expectedHash != null && value != null && expectedHash.equals(hash(value));
    }

    public static String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            List<String> keys = new ArrayList<>(map.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(quote(keys.get(i))).append(':').append(canonical(map.get(keys.get(i))));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object e : (Iterable<Object>) value) {
                if (!first) sb.append(',');
                sb.append(canonical(e));
                first = false;
            }
            return sb.append(']').toString();
        }
        if (value instanceof String) return quote((String) value);
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isInfinite(d) && d == Math.floor(d)) return Long.toString((long) d);
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return quote(value.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
