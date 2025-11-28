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
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
abstract class BaseIntrospectionTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseIntrospectionTokenService.class);
    private static final long OFFLINE_VERIFICATION_TIMER_SECONDS = 10;
    static final String LEGACY_RFC8707_ENABLED = "legacy.rfc8707.enabled";

    private final JWTService jwtService;
    private final ClientSyncService clientService;
    private final ProtectedResourceManager protectedResourceManager;
    private final TokenType tokenType;
    private final boolean isLegacyRfc8707Enabled;
    
    BaseIntrospectionTokenService(TokenType tokenType,
                                  JWTService jwtService,
                                  ClientSyncService clientService,
                                  ProtectedResourceManager protectedResourceManager,
                                  Environment environment) {
        this.tokenType = tokenType;
        this.jwtService = jwtService;
        this.clientService = clientService;
        this.protectedResourceManager = protectedResourceManager;
        this.isLegacyRfc8707Enabled = environment.getProperty(LEGACY_RFC8707_ENABLED, Boolean.class, true);
    }

    protected abstract Maybe<? extends Token> findByToken(String token);

    protected Maybe<JWT> introspectToken(String token, boolean offlineVerification, String callerClientId) {
        return jwtService.decode(token, tokenType)
                .flatMap(jwt -> validateAudienceAndGetCertificateId(jwt, callerClientId)
                        .flatMap(certificateId -> jwtService.decodeAndVerify(token, () -> certificateId, tokenType)))
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

    private Single<String> validateAudienceAndGetCertificateId(JWT jwt, String callerClientId) {
        List<String> audiences = jwt.getAudList();

        if (audiences.isEmpty()) {
            return Single.error(new InvalidTokenException("The token is invalid", "Token has no audience claim", jwt));
        }

        // Single-audience: check if the audience is a client ID
        if (audiences.size() == 1) {
            return clientService.findByDomainAndClientId(jwt.getDomain(), audiences.getFirst())
                    .map(Client::getCertificate)
                    .switchIfEmpty(Single.defer(() -> validateProtectedResourcesAndGetCertificateId(jwt, callerClientId)));
        }

        return validateProtectedResourcesAndGetCertificateId(jwt, callerClientId);
    }

    private Single<String> validateProtectedResourcesAndGetCertificateId(JWT jwt, String callerClientId) {
        return validateResourcesBelongToDomain(jwt)
                .flatMapCompletable(matchedResources -> {
                    if (isLegacyRfc8707Enabled && callerClientId != null) {
                        return validateResourcesBelongToAuthorizedClient(matchedResources, jwt, callerClientId);
                    }
                    return Completable.complete();
                })
                .toSingle(() -> {
                    // Protected resources currently do not have a configurable certificate ID
                    return "";
                });
    }

    private Single<Set<ProtectedResource>> validateResourcesBelongToDomain(JWT jwt) {
        List<String> audiences = jwt.getAudList();
        String domainId = jwt.getDomain();
        Set<ProtectedResource> matchedResources = new HashSet<>();
        Set<String> unmatchedAudiences = new HashSet<>();

        for (String audience : audiences) {
            Set<ProtectedResource> resources = protectedResourceManager.getByIdentifier(audience);
            if (!resources.isEmpty()) {
                matchedResources.addAll(resources);
            } else {
                unmatchedAudiences.add(audience);
            }
        }

        if (!unmatchedAudiences.isEmpty()) {
            String details = String.format("Token audience values [%s] do not match any client or protected resource identifiers in domain [%s]",
                    String.join(", ", unmatchedAudiences), domainId);
            return Single.error(new InvalidTokenException("The token is invalid", details, jwt));
        }

        return Single.just(matchedResources);
    }

    private Completable validateResourcesBelongToAuthorizedClient(Set<ProtectedResource> matchedResources, JWT jwt, @NotNull String callerClientId) {
        Set<String> mismatchedClientIds = matchedResources.stream()
                .map(ProtectedResource::getClientId)
                .filter(clientId -> !callerClientId.equals(clientId))
                .collect(Collectors.toSet());

        if (!mismatchedClientIds.isEmpty()) {
            String details = String.format("Protected resources matched by token audience have client IDs [%s] that do not match the introspecting client ID [%s]",
                    String.join(", ", mismatchedClientIds), callerClientId);
            return Completable.error(new InvalidTokenException("The token is invalid", details, jwt));
        }

        return Completable.complete();
    }
}
