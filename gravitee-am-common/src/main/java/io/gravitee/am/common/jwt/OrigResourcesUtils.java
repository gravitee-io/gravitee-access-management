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
package io.gravitee.am.common.jwt;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilities to handle orig_resources claim parsing and normalization.
 */
public final class OrigResourcesUtils {

    private OrigResourcesUtils() {}

    /**
     * Extracts the orig_resources claim from a JWT map into a Set<String>.
     * Accepts both single String and List forms, ignores non-string entries.
     */
    public static Set<String> extractOrigResources(Map<String, Object> jwt) {
        Set<String> resources = new HashSet<>();
        if (jwt == null || !jwt.containsKey(Claims.ORIG_RESOURCES)) {
            return resources;
        }
        Object claim = jwt.get(Claims.ORIG_RESOURCES);
        if (claim == null) {
            return resources;
        }
        if (claim instanceof java.util.List) {
            for (Object v : (java.util.List<?>) claim) {
                if (v instanceof String) {
                    resources.add((String) v);
                }
            }
        } else if (claim instanceof String) {
            resources.add((String) claim);
        }
        return resources;
    }
}


