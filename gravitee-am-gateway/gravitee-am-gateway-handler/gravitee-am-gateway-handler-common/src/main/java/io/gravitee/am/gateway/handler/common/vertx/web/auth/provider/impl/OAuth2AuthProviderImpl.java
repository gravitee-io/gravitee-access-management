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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTCache;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class OAuth2AuthProviderImpl implements OAuth2AuthProvider {

    @Autowired
    @Qualifier("AccessTokenIntrospection")
    private IntrospectionTokenService introspectionTokenService;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private ProtectedResourceSyncService protectedResourceSyncService;

    @Autowired
    private JWTCache jwtCache;

    @Override
    public void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
        introspect(token, offlineVerification)
                .flatMap(jwt -> clientSyncService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())
                .switchIfEmpty(protectedResourceSyncService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud()))
                .switchIfEmpty(Maybe.error(new InvalidTokenException("The token is invalid", "Client or resource not found: " + jwt.getAud(), jwt)))
                .map(client -> new OAuth2AuthResponse(jwt, client)))
                .subscribe(
                        accessToken -> {
                            try {
                                jwtCache.put(token, accessToken.getToken().getExp());
                            } catch (Exception e) {
                                log.error("Could not add token to cache, err={}", e.getMessage());
                            } finally {
                                handler.handle(Future.succeededFuture(accessToken));
                            }
                        },
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private Maybe<JWT> introspect(String token, boolean offlineVerification) {
        return jwtCache.isPresent(token)
                .onErrorResumeNext(err -> Single.just(false))
                .flatMapMaybe(jtiCached -> jtiCached ?
                        introspectionTokenService.introspect(token, true) :
                        introspectionTokenService.introspect(token, offlineVerification));
    }
}
