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
package io.gravitee.am.gateway.handler.oauth2.request;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.model.Client;
import io.reactivex.Single;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequestResolver {

    public Single<TokenRequest> resolve(TokenRequest tokenRequest, Client client) {
        return resolveAuthorizedScopes(tokenRequest, client);
    }

    /**
     * If the client omits the scope parameter when requesting authorization, the authorization server MUST either process the
     * request using a pre-defined default value or fail the request indicating an invalid scope.
     * See <a href="https://tools.ietf.org/html/rfc6749#section-3.3">3.3. Access Token Scope</a>
     *
     * @param tokenRequest the token request to resolve
     * @param client the client which trigger the request
     * @return the authorization request
     */
    private Single<TokenRequest> resolveAuthorizedScopes(TokenRequest tokenRequest, Client client) {
        List<String> clientScopes = client.getScopes();
        Set<String> requestScopes = tokenRequest.getScopes();
        if (clientScopes != null && !clientScopes.isEmpty()) {
            // no requested scope, set default client scopes to the request
            if (requestScopes == null || requestScopes.isEmpty()) {
                requestScopes = new HashSet<>(clientScopes);
                tokenRequest.setScopes(requestScopes);
            } else {
                for (String scope : requestScopes) {
                    if (!clientScopes.contains(scope)) {
                        return Single.error(new InvalidScopeException("Invalid scope: " + scope));
                    }
                }
            }
        }
        if (requestScopes == null || requestScopes.isEmpty()) {
            return Single.error(new InvalidScopeException("Empty scope (either the client or the user is not allowed the requested scopes)"));
        }
        return Single.just(tokenRequest);
    }
}
