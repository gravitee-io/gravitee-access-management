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
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.reactivex.Single;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractRequestResolver<R extends BaseRequest> {

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
        final List<String> clientScopes = client.getScopes();
        final Set<String> requestScopes = request.getScopes();
        Set<String> clientResolvedScopes = new HashSet<>();
        Set<String> resolvedScopes = new HashSet<>();
        Set<String> invalidScopes = new HashSet<>();
        // client scopes
        if (clientScopes != null && !clientScopes.isEmpty()) {
            // no requested scope, set default client scopes to the request
            if (requestScopes == null || requestScopes.isEmpty()) {
                resolvedScopes.addAll(new HashSet<>(clientScopes));
            } else {
                // filter the actual scopes granted by the client
                for (String scope : requestScopes) {
                    if (clientScopes.contains(scope)) {
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
                        .map(role -> role.getPermissions() != null ? role.getPermissions() : Collections.<String>emptyList())
                        .flatMap(List::stream)
                        .collect(Collectors.toSet());
                // no requested scope, set default user permissions scopes to the request
                if (requestScopes == null || requestScopes.isEmpty()) {
                    resolvedScopes.addAll(new HashSet<>(permissions));
                } else {
                    // filter the actual scopes granted by the resource owner
                    requestScopes.forEach(scope -> {
                        if (permissions.contains(scope)) {
                            // scope can be rejected by the client but approved by the resource owner
                            resolvedScopes.add(scope);
                            invalidScopes.remove(scope);
                        } else {
                            if (!clientResolvedScopes.contains(scope)) {
                                invalidScopes.add(scope);
                            }
                        }
                    });
                }
            }
        }

        if (!invalidScopes.isEmpty()) {
            return Single.error(new InvalidScopeException("Invalid scope(s): " + invalidScopes.stream().collect(Collectors.joining(SCOPE_DELIMITER))));
        }

        if (resolvedScopes.isEmpty() && (requestScopes != null && !requestScopes.isEmpty())) {
            return Single.error(new InvalidScopeException("Invalid scope(s): " + requestScopes.stream().collect(Collectors.joining(SCOPE_DELIMITER))));
        }

        // set resolved scopes
        request.setScopes(resolvedScopes);

        return Single.just(request);
    }
}
