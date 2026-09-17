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
package io.gravitee.am.service.reporter.attribute;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a resolved value into data a reporter can export: single values, and lists and maps nested around them.
 *
 * @author GraviteeSource Team
 */
public final class ExportableValue {

    /** Levels of nesting below the top-level value. */
    static final int MAX_DEPTH = 10;

    /** Elements across the whole value, at any depth. */
    static final int MAX_NODES = 100;

    private ExportableValue() {
    }

    /**
     * @return an immutable copy of {@code value}, or empty if any part of it cannot be exported
     */
    public static Optional<Object> of(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(copyOf(value, MAX_DEPTH, new int[]{0}));
        } catch (NotExportable ex) {
            return Optional.empty();
        }
    }

    private static Object copyOf(Object value, int remainingDepth, int[] nodes) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            if (remainingDepth < 0) {
                throw new NotExportable();
            }
            return value instanceof Map<?, ?> map
                    ? copyOf(map, remainingDepth, nodes)
                    : copyOf((Collection<?>) value, remainingDepth, nodes);
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof Date date) {
            return new Date(date.getTime());
        }
        if (isScalar(value)) {
            return value;
        }
        throw new NotExportable();
    }

    private static Map<String, Object> copyOf(Map<?, ?> map, int remainingDepth, int[] nodes) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, element) -> {
            if (!(key instanceof CharSequence name)) {
                throw new NotExportable();
            }
            count(nodes);
            copy.put(name.toString(), copyOf(element, remainingDepth - 1, nodes));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static List<Object> copyOf(Collection<?> collection, int remainingDepth, int[] nodes) {
        List<Object> copy = new ArrayList<>(collection.size());
        for (Object element : collection) {
            count(nodes);
            copy.add(copyOf(element, remainingDepth - 1, nodes));
        }
        return Collections.unmodifiableList(collection instanceof Set<?> ? sortedIfComparable(copy) : copy);
    }

    private static void count(int[] nodes) {
        if (++nodes[0] > MAX_NODES) {
            throw new NotExportable();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> sortedIfComparable(List<Object> elements) {
        List<Object> sorted = new ArrayList<>(elements);
        try {
            sorted.sort((Comparator) Comparator.nullsLast(Comparator.naturalOrder()));
            return sorted;
        } catch (ClassCastException ex) {
            return elements;
        }
    }

    private static boolean isScalar(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Temporal;
    }

    private static final class NotExportable extends RuntimeException {
        NotExportable() {
            super(null, null, false, false);
        }
    }
}
