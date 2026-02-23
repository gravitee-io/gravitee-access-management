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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default validator for JWT-based tokens (access, refresh, id, jwt).
 *
 * Validates tokens in two stages:
 * 1. First attempts validation against the domain's own certificate (domain-issued tokens).
 * 2. If that fails and trusted issuers are configured, decodes the JWT without verification
 *    to extract the "iss" claim, then validates against the matching trusted issuer's key material.
 *
 * @author GraviteeSource Team
 */
public class DefaultTokenValidator implements TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTokenValidator.class);

    private final JWTService jwtService;
    private final JWTService.TokenType jwtTokenType;
    private final String supportedTokenType;
    private final TrustedIssuerResolver trustedIssuerResolver;

    public DefaultTokenValidator(JWTService jwtService,
                                 JWTService.TokenType jwtTokenType,
                                 String supportedTokenType,
                                 TrustedIssuerResolver trustedIssuerResolver) {
        this.jwtService = jwtService;
        this.jwtTokenType = jwtTokenType;
        this.supportedTokenType = supportedTokenType;
        this.trustedIssuerResolver = trustedIssuerResolver;
    }

    @Override
    public String getSupportedTokenType() {
        return supportedTokenType;
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
        // Stage 1: Try domain certificate validation (existing path for domain-issued tokens)
        return jwtService.decodeAndVerify(token, () -> null, jwtTokenType)
                .map(jwt -> buildValidatedToken(jwt, domain, null))
                .onErrorResumeNext(domainError -> {
                    // Stage 2: If trusted issuers are configured, try trusted issuer validation
                    if (hasTrustedIssuers(settings)) {
                        return validateWithTrustedIssuer(token, settings, domain);
                    }
                    // No trusted issuers — return original domain validation error
                    LOGGER.debug("Failed to validate {}: {}", supportedTokenType, domainError.getMessage());
                    return Single.error(new InvalidGrantException("Invalid " + supportedTokenType + ": " + domainError.getMessage()));
                });
    }

    private Single<ValidatedToken> validateWithTrustedIssuer(String token, TokenExchangeSettings settings, Domain domain) {
        // Decode without verification to extract the issuer
        return jwtService.decode(token, jwtTokenType)
                .flatMap(jwt -> {
                    String issuer = jwt.getIss();
                    if (issuer == null || issuer.isBlank()) {
                        return Single.error(new InvalidGrantException("JWT missing 'iss' claim"));
                    }

                    TrustedIssuer matchingIssuer = findTrustedIssuer(settings, issuer);
                    if (matchingIssuer == null) {
                        return Single.error(new InvalidGrantException("Untrusted issuer: " + issuer));
                    }

                    return Single.fromCallable(() -> {
                        JWTClaimsSet claimsSet = trustedIssuerResolver.resolve(token, matchingIssuer);
                        return buildValidatedTokenFromClaims(claimsSet, domain, matchingIssuer);
                    });
                })
                .onErrorResumeNext(error -> {
                    if (error instanceof InvalidGrantException) {
                        return Single.error(error);
                    }
                    LOGGER.debug("Failed to decode JWT for trusted issuer validation: {}", error.getMessage());
                    return Single.error(new InvalidGrantException("Invalid " + supportedTokenType + ": " + error.getMessage()));
                });
    }

    private void validateTemporalClaims(long exp, long nbf) {
        long currentTime = System.currentTimeMillis() / 1000;
        if (exp > 0 && exp < currentTime) {
            throw new InvalidGrantException(supportedTokenType + " has expired");
        }
        if (nbf > 0 && nbf > currentTime) {
            throw new InvalidGrantException(supportedTokenType + " is not yet valid");
        }
    }

    private ValidatedToken buildValidatedToken(JWT jwt, Domain domain,
                                                TrustedIssuer matchingIssuer) {
        return buildValidatedToken(new HashMap<>(jwt), jwt.getExp(), jwt.getIat(), jwt.getNbf(),
                domain, matchingIssuer);
    }

    private ValidatedToken buildValidatedTokenFromClaims(JWTClaimsSet claimsSet, Domain domain,
                                                          TrustedIssuer matchingIssuer) {
        long exp = claimsSet.getExpirationTime() != null ? claimsSet.getExpirationTime().getTime() / 1000 : 0;
        long iat = claimsSet.getIssueTime() != null ? claimsSet.getIssueTime().getTime() / 1000 : 0;
        long nbf = claimsSet.getNotBeforeTime() != null ? claimsSet.getNotBeforeTime().getTime() / 1000 : 0;
        return buildValidatedToken(new HashMap<>(claimsSet.getClaims()), exp, iat, nbf,
                domain, matchingIssuer);
    }

    private ValidatedToken buildValidatedToken(Map<String, Object> claims, long exp, long iat, long nbf,
                                                Domain domain, TrustedIssuer matchingIssuer) {
        validateTemporalClaims(exp, nbf);

        Set<String> scopes = applyScopeMapping(parseScopes(claims.get(Claims.SCOPE)), matchingIssuer);
        List<String> audience = parseAudience(claims.get(Claims.AUD));
        Object clientId = claims.get(Claims.CLIENT_ID);

        return ValidatedToken.builder()
                .subject((String) claims.get(Claims.SUB))
                .issuer((String) claims.get(Claims.ISS))
                .claims(claims)
                .scopes(scopes)
                .expiration(exp > 0 ? new Date(exp * 1000) : null)
                .issuedAt(iat > 0 ? new Date(iat * 1000) : null)
                .notBefore(nbf > 0 ? new Date(nbf * 1000) : null)
                .tokenId((String) claims.get(Claims.JTI))
                .audience(audience)
                .clientId(clientId != null ? clientId.toString() : null)
                .tokenType(supportedTokenType)
                .domain(domain.getId())
                .trustedIssuer(matchingIssuer)
                .build();
    }

    /**
     * Apply per-issuer scope mapping. If the issuer has scope mappings configured,
     * only mapped scopes are returned (unmapped scopes are dropped — fail-closed).
     * If no scope mappings are configured, all scopes pass through unchanged.
     */
    private Set<String> applyScopeMapping(Set<String> originalScopes, TrustedIssuer issuer) {
        if (issuer == null) {
            return originalScopes;
        }
        Map<String, String> mappings = issuer.getScopeMappings();
        if (mappings == null || mappings.isEmpty()) {
            return originalScopes;
        }
        return originalScopes.stream()
                .map(mappings::get)
                .filter(mapped -> mapped != null)
                .collect(Collectors.toSet());
    }

    private static boolean hasTrustedIssuers(TokenExchangeSettings settings) {
        return settings != null
                && settings.getTrustedIssuers() != null
                && !settings.getTrustedIssuers().isEmpty();
    }

    private static TrustedIssuer findTrustedIssuer(TokenExchangeSettings settings, String issuer) {
        return settings.getTrustedIssuers().stream()
                .filter(ti -> issuer.equals(ti.getIssuer()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseScopes(Object scopeClaim) {
        return switch (scopeClaim) {
            case null -> Collections.emptySet();
            case String s -> new HashSet<>(Arrays.asList(s.split("\\s+")));
            case List<?> list -> new HashSet<>((List<String>) list);
            default -> Collections.emptySet();
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAudience(Object audClaim) {
        return switch (audClaim) {
            case null -> Collections.emptyList();
            case String s -> Collections.singletonList(s);
            case List<?> list -> (List<String>) list;
            default -> Collections.emptyList();
        };
    }
}
