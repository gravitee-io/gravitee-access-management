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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record IdJagTarget(
        String audience,
        String resource,
        String clientId,
        Map<String, String> scopeMappings,
        String audSubMapping
) {

    public IdJagTarget {
        scopeMappings = scopeMappings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(scopeMappings));
    }

    public void requireMapped(Collection<String> domainScopes) {
        Set<String> unmapped = domainScopes.stream()
                .filter(scope -> !scopeMappings.containsKey(scope))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!unmapped.isEmpty()) {
            throw new InvalidScopeException("Scope has no mapping at audience " + audience + ": " + String.join(" ", unmapped));
        }
    }

    public String partnerScope(Collection<String> domainScopes) {
        return scopeMappings.entrySet().stream()
                .filter(mapping -> domainScopes.contains(mapping.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .collect(Collectors.joining(" "));
    }
}
