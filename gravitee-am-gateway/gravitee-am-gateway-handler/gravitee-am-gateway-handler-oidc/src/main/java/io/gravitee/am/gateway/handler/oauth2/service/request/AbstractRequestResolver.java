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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.utils.ParameterizedScopeUtils;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractRequestResolver<R extends OAuth2Request> {

    private ScopeManager scopeManager;

    public void setScopeManager(ScopeManager scopeManager) {
        this.scopeManager = scopeManager;
    }

    /**
     * If the client omits the scope parameter when requesting authorization, the authorization server MUST either process the
     * request using a pre-defined default value or fail the request indicating an invalid scope.
     * See <a href="https://tools.ietf.org/html/rfc6749#section-3.3">3.3. Access Token Scope</a>
     *
     * @param request the request to resolve
     * @param client the client which trigger the request
     * @return the oauth 2.0 request
     */
    protected Single<R> resolveAuthorizedScopes(R request, Client client, User endUser) {
        final Set<String> requestScopes = request.getScopes();
        Set<String> clientResolvedScopes = new HashSet<>();
        Set<String> resolvedScopes = new HashSet<>();
        Set<String> invalidScopes = new HashSet<>();
        // client scopes
        if (client.getScopeSettings() != null && !client.getScopeSettings().isEmpty()) {
            final List<String> clientScopes = client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).collect(Collectors.toList());
            final List<String> defaultScopes = client.getScopeSettings().stream().filter(ApplicationScopeSettings::isDefaultScope).map(ApplicationScopeSettings::getScope).collect(Collectors.toList());
            final List<String> parameterizedScopes = this.scopeManager == null ? new ArrayList<>() : client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).filter(scopeManager::isParameterizedScope).collect(Collectors.toList());

            // no requested scope, set default client scopes to the request
            if (requestScopes == null || requestScopes.isEmpty()) {
                resolvedScopes.addAll(new HashSet<>(defaultScopes));
            } else {
                // filter the actual scopes granted by the client
                for (String scope : requestScopes) {
                    if (clientScopes.contains(scope) || ParameterizedScopeUtils.isParameterizedScope(parameterizedScopes, scope)) {
                        resolvedScopes.add(scope);
                        clientResolvedScopes.add(scope);
                    } else {
                        invalidScopes.add(scope);
                    }
                }
            }
        }

        // user scopes
        if (endUser != null && client.isEnhanceScopesWithUserPermissions()) {
            Set<Role> roles = endUser.getRolesPermissions();
            if (roles != null && !roles.isEmpty()) {
                Set<String> permissions = roles.stream()
                        .map(role -> role.getOauthScopes() != null ? role.getOauthScopes() : Collections.<String>emptyList())
                        .flatMap(List::stream)
                        .collect(Collectors.toSet());

                if (requestScopes != null) {
                	// filter the actual scopes granted by the resource owner
                    requestScopes.forEach(scope -> {
                        if (!permissions.contains(scope) && !clientResolvedScopes.contains(scope)) {
                        	invalidScopes.add(scope);
                        }
                    });
                }

                // The request must be enhanced with all of user's permissions
                invalidScopes.removeAll(permissions);
                resolvedScopes.addAll(permissions);
            }
        }

        if (!invalidScopes.isEmpty()) {
            return Single.error(new InvalidScopeException("Invalid scope(s): " + invalidScopes.stream().collect(Collectors.joining(SCOPE_DELIMITER))));
        }

        if (resolvedScopes.isEmpty() && (requestScopes != null && !requestScopes.isEmpty())) {
            return Single.error(new InvalidScopeException("Invalid scope(s): " + requestScopes.stream().collect(Collectors.joining(SCOPE_DELIMITER))));
        }

        // only put default values if there is no requested scopes
        if (requestScopes == null || requestScopes.isEmpty()) {
            request.setScopes(resolvedScopes);
        }

        return Single.just(request);
    }


}
