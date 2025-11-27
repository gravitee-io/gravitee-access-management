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
package io.gravitee.am.gateway.handler.common.oauth2.impl;

import io.gravitee.am.common.exception.jwt.JWTException;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
abstract class BaseIntrospectionTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseIntrospectionTokenService.class);
    private static final long OFFLINE_VERIFICATION_TIMER_SECONDS = 10;

    private final JWTService jwtService;
    private final ClientSyncService clientService;
    private final TokenType tokenType;
    BaseIntrospectionTokenService(TokenType tokenType,
                                  JWTService jwtService,
                                  ClientSyncService clientService) {
        this.tokenType = tokenType;
        this.jwtService = jwtService;
        this.clientService = clientService;
    }
    protected abstract Maybe<? extends Token> findByToken(String token);

    protected Maybe<JWT> introspectToken(String token, boolean offlineVerification) {
        return jwtService.decode(token, tokenType)
                .flatMap(jwt -> jwtService.decodeAndVerify(token, () -> getClientCertificateId(jwt).blockingGet(), tokenType))
                .toMaybe()
                .flatMap(jwt -> {
                    // Just check the JWT signature and JWT validity if offline verification option is enabled
                    // or if the token has just been created (could not be in database so far because of async database storing process delay)
                    if (offlineVerification || Instant.now().isBefore(Instant.ofEpochSecond(jwt.getIat() + OFFLINE_VERIFICATION_TIMER_SECONDS))) {
                        return Maybe.just(jwt);
                    }

                    // check if token is not revoked
                    return findByToken(jwt.getJti())
                            .switchIfEmpty(Maybe.error(() -> new InvalidTokenException("The token is invalid", "Token with JTI [" + jwt.getJti() + "] not found in the database", jwt)))
                            .map(accessToken -> {
                                if (accessToken.getExpireAt().before(new Date())) {
                                    throw new InvalidTokenException("The token expired", "Token with JTI [" + jwt.getJti() + "] is expired", jwt);
                                }
                                return jwt;
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        LOGGER.debug("An error occurs while decoding JWT access token : {}", token, ex);
                        return Maybe.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    if (ex instanceof InvalidTokenException) {
                        InvalidTokenException invalidTokenException = (InvalidTokenException) ex;
                        String details = invalidTokenException.getDetails();
                        JWT jwt = invalidTokenException.getJwt();
                        LOGGER.debug("An error occurs while checking JWT access token validity: {}\n\t - details: {}\n\t - decoded jwt: {}",
                                token, details != null ? details : "none", jwt != null ? jwt.toString() : "{}", invalidTokenException);
                    }
                    return Maybe.error(ex);
                });
    }

    private Maybe<String> getClientCertificateId(JWT jwt) {
        return clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())
                .switchIfEmpty(Maybe.error(() -> new InvalidTokenException("Invalid or unknown client for this token")))
                .map(Client::getCertificate);
    }
}
