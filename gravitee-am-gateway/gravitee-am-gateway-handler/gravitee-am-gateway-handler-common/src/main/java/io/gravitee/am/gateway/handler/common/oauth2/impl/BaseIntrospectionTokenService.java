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
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionResult;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.Token;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
abstract class BaseIntrospectionTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseIntrospectionTokenService.class);
    static final String LEGACY_RFC8707_ENABLED = "legacy.rfc8707.enabled";
    static final String OFFLINE_VERIFICATION_TIMER_SECONDS_KEY = "handlers.oauth2.introspect.offlineVerificationTimerSeconds";

    private final JWTService jwtService;
    private final ClientLookupService clientLookupService;
    private final ProtectedResourceManager protectedResourceManager;
    private final TokenType tokenType;
    private final boolean isLegacyRfc8707Enabled;
    private final int offlineVerificationTimerSeconds;
    
    BaseIntrospectionTokenService(TokenType tokenType,
                                  JWTService jwtService,
                                  ClientLookupService clientLookupService,
                                  ProtectedResourceManager protectedResourceManager,
                                  Environment environment) {
        this.tokenType = tokenType;
        this.jwtService = jwtService;
        this.clientLookupService = clientLookupService;
        this.protectedResourceManager = protectedResourceManager;
        this.isLegacyRfc8707Enabled = environment.getProperty(LEGACY_RFC8707_ENABLED, Boolean.class, true);
        this.offlineVerificationTimerSeconds = environment.getProperty(OFFLINE_VERIFICATION_TIMER_SECONDS_KEY, Integer.class, 10);
    }

    protected abstract Maybe<? extends Token> findByToken(String token);

    protected Maybe<IntrospectionResult> introspectToken(String token, boolean offlineVerification, String callerClientId) {
        return jwtService.decode(token, tokenType)
                .flatMap(jwt -> validateAudienceAndGetCertificateId(jwt, callerClientId)
                        .flatMap(certificateId -> jwtService.decodeAndVerify(token, () -> certificateId, tokenType)))
                .toMaybe()
                .flatMap(jwt -> {
                    // Just check the JWT signature and JWT validity if offline verification option is enabled
                    // or if the token has just been created (could not be in database so far because of async database storing process delay)
                    if (offlineVerification || Instant.now().isBefore(Instant.ofEpochSecond(jwt.getIat() + offlineVerificationTimerSeconds))) {
                        return Maybe.just(new IntrospectionResult(jwt, null));
                    }

                    // check if token is not revoked
                    return findByToken(jwt.getJti())
                            .switchIfEmpty(Maybe.error(() -> new InvalidTokenException("The token is invalid", "Token with JTI [" + jwt.getJti() + "] not found in the database", jwt)))
                            .map(accessToken -> {
                                if (accessToken.getExpireAt().before(new Date())) {
                                    throw new InvalidTokenException("The token expired", "Token with JTI [" + jwt.getJti() + "] is expired", jwt);
                                }
                                return new IntrospectionResult(jwt, accessToken.getClient());
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.warn("An error occurs while decoding JWT access token : {}", token, ex);
                        } else {
                            LOGGER.warn("An error occurs while decoding JWT access token", ex);
                        }
                        return Maybe.error(new InvalidTokenException(ex.getMessage(), ex));
                    } else if (ex instanceof InvalidTokenException invalidTokenException) {
                        String details = invalidTokenException.getDetails();
                        JWT jwt = invalidTokenException.getJwt();
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.warn("An error occurs while checking JWT access token validity: {}\n\t - details: {}\n\t - decoded jwt: {}",
                                    token, details != null ? details : "none", jwt != null ? jwt.toString() : "{}", invalidTokenException);
                        } else {
                            LOGGER.warn("An error occurs while checking JWT access token validity", invalidTokenException);
                        }
                    } else {
                        if ( LOGGER.isDebugEnabled()) {
                            LOGGER.warn("An unexpected error occurred while introspecting JWT access token: {}", token, ex);
                        } else {
                            LOGGER.warn("An unexpected error occurred while introspecting JWT access token", ex);
                        }
                    }
                    return Maybe.error(ex);
                });
    }

<<<<<<< HEAD
    private Single<String> validateAudienceAndGetCertificateId(JWT jwt, String callerClientId) {
        List<String> audiences = jwt.getAudList();

        if (audiences.isEmpty()) {
            return Single.error(new InvalidTokenException("The token is invalid", "Token has no audience claim", jwt));
        }

        return Observable.fromIterable(audiences)
                .concatMapSingle(audience -> resolveAudience(jwt.getDomain(), audience))
                .toList()
                .flatMap(matches -> resolveCertificateIdFromAudMatches(matches, jwt, callerClientId));
=======
    private Maybe<String> getClientCertificateId(JWT jwt) {
        return clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())
                .switchIfEmpty(Maybe.error(() -> new InvalidTokenException("Invalid or unknown client for this token")))
                .flatMap(client -> client.getCertificate() != null
                        ? Maybe.just(client.getCertificate())
                        : Maybe.empty());
>>>>>>> 803f101dc (fix: master domain should introspect token generated in all other domains)
    }

    private Single<AudienceMatch> resolveAudience(String domain, String audience) {
        return clientLookupService.findByDomainAndClientId(domain, audience)
                .onErrorResumeNext(err -> {
                    if (err instanceof InvalidClientMetadataException) {
                        LOGGER.debug("Introspection: audience [{}] could not be resolved as a client ({}); falling back to protected-resource validation",
                                audience, err.getMessage());
                        return Maybe.empty();
                    }
                    return Maybe.error(err);
                })
                .<AudienceMatch>map(client -> new ClientMatch(audience, client))
                .switchIfEmpty(Single.fromCallable(() -> {
                    Set<ProtectedResource> resources = protectedResourceManager.getByIdentifier(audience);
                    if (!resources.isEmpty()) {
                        return new ResourceMatch(audience, resources);
                    }
                    return new UnmatchedAudience(audience);
                }));
    }

    private Single<String> resolveCertificateIdFromAudMatches(List<AudienceMatch> matches, JWT jwt, String callerClientId) {
        Set<String> unmatched = matches.stream()
                .filter(UnmatchedAudience.class::isInstance)
                .map(AudienceMatch::audience)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!unmatched.isEmpty()) {
            String details = String.format("Token audience values [%s] do not match any client or protected resource identifiers in domain [%s]",
                    String.join(", ", unmatched), jwt.getDomain());
            return Single.error(new InvalidTokenException("The token is invalid", details, jwt));
        }

        // Use first matched client's certificate.
        // Protected-resource-only tokens fall back to "" (HMAC/default certificate).
        String certificateId = matches.stream()
                .filter(ClientMatch.class::isInstance)
                .map(ClientMatch.class::cast)
                .map(cm -> cm.client().getCertificate())
                .map(cert -> Objects.requireNonNullElse(cert, ""))
                .findFirst()
                .orElse("");

        return validateResourcesBelongToAuthorizedClient(matches, jwt, callerClientId)
                .toSingleDefault(certificateId);
    }

    private Completable validateResourcesBelongToAuthorizedClient(List<AudienceMatch> matches, JWT jwt, String callerClientId) {
        if (!isLegacyRfc8707Enabled || callerClientId == null) {
            return Completable.complete();
        }

        Set<String> mismatchedClientIds = matches.stream()
                .filter(ResourceMatch.class::isInstance)
                .map(ResourceMatch.class::cast)
                .flatMap(rm -> rm.resources().stream())
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

    private sealed interface AudienceMatch permits ClientMatch, ResourceMatch, UnmatchedAudience {
        String audience();
    }

    private record ClientMatch(String audience, Client client) implements AudienceMatch { }
    private record ResourceMatch(String audience, Set<ProtectedResource> resources) implements AudienceMatch { }
    private record UnmatchedAudience(String audience) implements AudienceMatch { }
}
