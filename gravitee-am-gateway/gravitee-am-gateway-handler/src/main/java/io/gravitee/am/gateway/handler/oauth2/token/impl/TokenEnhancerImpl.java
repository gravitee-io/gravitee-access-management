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
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEnhancerImpl implements TokenEnhancer {

    @Autowired
    private ClientService clientService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IDTokenService idTokenService;

    @Override
    public Single<AccessToken> enhance(AccessToken accessToken, OAuth2Request oAuth2Request) {
        return clientService.findByClientId(oAuth2Request.getClientId())
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(oAuth2Request.getClientId())))
                .flatMapSingle(client -> {
                    if (!oAuth2Request.isClientOnly()) {
                        return userService.findById(oAuth2Request.getSubject())
                                .switchIfEmpty(Maybe.error(new UserNotFoundException(oAuth2Request.getSubject())))
                                .toSingle()
                                .map(user -> new TokenEnhancerData(client, user));
                    } else {
                        return Single.just(new TokenEnhancerData(client, null));
                    }
                })
                .flatMap(tokenEnhancerData -> Single.just(tokenEnhancerData.getUser() == null)
                        .flatMap(isClientOnly -> {
                            if (!isClientOnly && tokenEnhancerData.getClient().isEnhanceScopesWithUserPermissions()) {
                                // enhance token scopes with user permissions
                                return enhanceScopes(accessToken, tokenEnhancerData.getUser(), oAuth2Request);
                            } else {
                                return Single.just(accessToken);
                            }
                        })
                        .flatMap(accessToken1 -> {
                            // add access token hash value
                            if (oAuth2Request.getResponseType() != null && ResponseType.CODE_ID_TOKEN_TOKEN.equals(oAuth2Request.getResponseType())) {
                                oAuth2Request.getContext().put(OIDCClaims.at_hash, accessToken1.getToken());
                            }

                            // enhance token with ID token
                            if (oAuth2Request.shouldGenerateIDToken()) {
                                return enhanceIDToken(accessToken1, tokenEnhancerData.getClient(), tokenEnhancerData.getUser(), oAuth2Request);
                            } else {
                                return Single.just(accessToken1);
                            }
                        }));

    }

    private Single<AccessToken> enhanceScopes(AccessToken accessToken, User user, OAuth2Request oAuth2Request) {
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            return roleService.findByIdIn(user.getRoles())
                    .zipWith((SingleSource<Set<String>>) observer -> {
                        // get requested scopes
                        Set<String> requestedScopes = new HashSet<>();
                        String scope = oAuth2Request.getRequestParameters().getFirst(OAuth2Constants.SCOPE);
                        if (scope != null) {
                            requestedScopes = new HashSet<>(Arrays.asList(scope.split(" ")));
                        }
                        observer.onSuccess(requestedScopes);
                    }, (roles, requestedScopes) -> {
                        Set<String> enhanceScopes = new HashSet<>(accessToken.getScopes());
                        enhanceScopes.addAll(roles.stream()
                                .map(r -> r.getPermissions())
                                .flatMap(List::stream)
                                .filter(permission -> {
                                    if (requestedScopes != null && !requestedScopes.isEmpty()) {
                                        return requestedScopes.contains(permission);
                                    }
                                    // if no query param scope, accept all enhance scopes
                                    return true;
                                })
                                .collect(Collectors.toList()));
                        accessToken.setScopes(enhanceScopes);
                        return accessToken;
                    });
        } else {
            return Single.just(accessToken);
        }
    }

    private Single<AccessToken> enhanceIDToken(AccessToken accessToken, Client client, User user, OAuth2Request oAuth2Request) {
        return idTokenService.create(oAuth2Request, client, user)
                .flatMap(idToken -> {
                    Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
                    additionalInformation.put(OAuth2Constants.ID_TOKEN, idToken);
                    accessToken.setAdditionalInformation(additionalInformation);
                    return Single.just(accessToken);
                });
    }

    private class TokenEnhancerData {
        private Client client;
        private User user;

        public TokenEnhancerData(Client client, User user) {
            this.client = client;
            this.user = user;
        }

        public Client getClient() {
            return client;
        }

        public void setClient(Client client) {
            this.client = client;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }
    }

}
