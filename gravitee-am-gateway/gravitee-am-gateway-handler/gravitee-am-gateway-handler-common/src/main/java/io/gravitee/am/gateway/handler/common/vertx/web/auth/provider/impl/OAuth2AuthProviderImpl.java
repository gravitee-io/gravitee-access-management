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

import io.gravitee.am.common.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2AuthProviderImpl implements OAuth2AuthProvider {

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ClientSyncService clientService;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Override
    public void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
        jwtService.decode(token)
                .flatMapMaybe(jwt -> clientService.findByClientId(jwt.getAud()))
                .switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token")))
                .flatMapSingle(client -> jwtService.decodeAndVerify(token, client)
                        .flatMap(jwt -> {
                            if (offlineVerification) {
                                return Single.just(jwt);
                            } else {
                                return accessTokenRepository.findByToken(jwt.getJti())
                                        .switchIfEmpty(Single.error(new InvalidTokenException("The token is invalid")))
                                        .map(accessToken -> {
                                            if (accessToken.getExpireAt().before(new Date())) {
                                                throw new InvalidTokenException("The token expired");
                                            }
                                            return jwt;
                                        });
                            }
                        }).map(jwt -> new OAuth2AuthResponse(jwt, client))
                )
                .subscribe(
                        accessToken -> handler.handle(Future.succeededFuture(accessToken)),
                        error -> handler.handle(Future.failedFuture(error)));
    }
}
