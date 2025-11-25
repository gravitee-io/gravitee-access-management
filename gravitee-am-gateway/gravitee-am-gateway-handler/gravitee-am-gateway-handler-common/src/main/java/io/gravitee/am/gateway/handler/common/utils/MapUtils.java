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
package io.gravitee.am.gateway.handler.common.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for Map operations
 *
 * @author GraviteeSource Team
 */
public class MapUtils {

    /**
     * Safely extracts a List of Strings from a Map, performing type checking to avoid unsafe casts.
     *
     * @param map the map to extract from
     * @param key the key to look up
     * @return an Optional containing the List of Strings if the value exists and is a valid List of Strings, empty otherwise
     */
    public static Optional<List<String>> extractStringList(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return Optional.empty();
        }

        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof List<?> list) {
            // Check if all elements are Strings
            if (list.stream().allMatch(String.class::isInstance)) {
                return Optional.of(list.stream()
                        .map(String.class::cast)
                        .collect(Collectors.toList()));
            }
        }

        return Optional.empty();
    }
}

