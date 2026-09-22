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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.impl;

import io.gravitee.am.extensiongrant.api.ProtectedResourceDirectory;
import io.gravitee.am.extensiongrant.api.ProtectedResourceScopes;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
class DomainProtectedResourceDirectory implements ProtectedResourceDirectory {

    private final ProtectedResourceManager protectedResourceManager;
    private final ScopeManager scopeManager;

    @Override
    public Optional<ProtectedResourceScopes> findByIdentifier(String resourceIdentifier) {
        if (protectedResourceManager.getByIdentifier(resourceIdentifier).isEmpty()) {
            return Optional.empty();
        }
        Set<String> scopes = protectedResourceManager.getScopesForResources(Set.of(resourceIdentifier));
        Set<String> parameterizedScopes = scopes.stream()
                .filter(scopeManager::isParameterizedScope)
                .collect(Collectors.toSet());
        return Optional.of(new ProtectedResourceScopes(scopes, parameterizedScopes));
    }
}
