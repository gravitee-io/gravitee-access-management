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

import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.service.utils.EvaluableRedirectUri;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestResolver extends AbstractRequestResolver<AuthorizationRequest> {

    public AuthorizationRequestResolver(ScopeManager scopeManager, ProtectedResourceManager protectedResourceManager) {
        this.setManagers(scopeManager, protectedResourceManager);
    }

    public Single<AuthorizationRequest> resolve(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return resolveAuthorizedScopes(authorizationRequest, client, endUser)
                .flatMap(request -> resolveRedirectUri(request, client));
    }

    /**
     * redirect_uri request parameter is OPTIONAL, but the RFC (rfc6749) assumes that
     * the request fails due to a missing, invalid, or mismatching redirection URI.
     * If no redirect_uri request parameter is supplied, the client must at least have one registered redirect uri
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
     *
     * @param authorizationRequest the authorization request to resolve
     * @param client the client which trigger the request
     * @return the authorization request
     */
    public Single<AuthorizationRequest> resolveRedirectUri(AuthorizationRequest authorizationRequest, Client client) {
        final String requestedRedirectUri = authorizationRequest.getRedirectUri();
        final List<String> registeredClientRedirectUris = client.getRedirectUris();
        // no redirect_uri parameter supplied, return the first client registered redirect uri
        if (requestedRedirectUri == null || requestedRedirectUri.isEmpty()) {
            authorizationRequest.setRedirectUri(registeredClientRedirectUris.iterator().next());
        }
        return Single.just(authorizationRequest);
    }

    public Single<AuthorizationRequest> evaluateELQueryParams(AuthorizationRequest authorizationRequest,
                                                              Client client,
                                                              ExecutionContext executionContext) {
        return matchWithEvaluableRedirectUri(client, authorizationRequest.getRedirectUri())
                .flatMap(uri -> uri.evaluate(executionContext))
                .map(redirectUri -> {
                    authorizationRequest.setRedirectUri(redirectUri);
                    return authorizationRequest;
                }).defaultIfEmpty(authorizationRequest);
    }

    private Maybe<EvaluableRedirectUri> matchWithEvaluableRedirectUri(Client client, String redirectUri) {
        return Flowable.fromIterable(client.getRedirectUris())
                .map(EvaluableRedirectUri::new)
                .filter(uri -> uri.matchRootUrl(redirectUri))
                .firstElement();
    }
}
