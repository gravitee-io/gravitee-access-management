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
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.TokenVerificationException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decorator that adds trusted issuer fallback validation to a {@link TokenValidator}.
 *
 * When the delegate's signature verification fails ({@link TokenVerificationException}) and
 * trusted issuers are configured, this decorator decodes the JWT without verification,
 * matches the issuer against configured trusted issuers, and validates against the
 * matching issuer's key material.
 *
 * Explicit rejections from the delegate (e.g., expired or not-yet-valid tokens) are
 * propagated as-is without attempting trusted issuer fallback.
 *
 * @author GraviteeSource Team
 */
public class TrustedIssuerTokenValidator implements TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustedIssuerTokenValidator.class);

    private final TokenValidator delegate;
    private final TrustedIssuerResolver trustedIssuerResolver;
    private final JWTService jwtService;
    private final JWTService.TokenType jwtTokenType;
    private final String supportedTokenType;

    public TrustedIssuerTokenValidator(TokenValidator delegate,
                                       TrustedIssuerResolver trustedIssuerResolver,
                                       JWTService jwtService,
                                       JWTService.TokenType jwtTokenType,
                                       String supportedTokenType) {
        this.delegate = delegate;
        this.trustedIssuerResolver = trustedIssuerResolver;
        this.jwtService = jwtService;
        this.jwtTokenType = jwtTokenType;
        this.supportedTokenType = supportedTokenType;
    }

    @Override
    public String getSupportedTokenType() {
        return supportedTokenType;
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
        return delegate.validate(token, settings, domain)
                .onErrorResumeNext(error -> {
                    if (error instanceof TokenVerificationException && hasTrustedIssuers(settings)) {
                        LOGGER.debug("Domain cert validation failed, trying trusted issuers: {}",
                                error.getMessage());
                        return validateWithTrustedIssuer(token, settings, domain);
                    }
                    return Single.error(error);
                });
    }

    private Single<ValidatedToken> validateWithTrustedIssuer(String token, TokenExchangeSettings settings, Domain domain) {
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
                        return buildValidatedToken(claimsSet, domain, matchingIssuer);
                    }).subscribeOn(Schedulers.io());
                })
                .onErrorResumeNext(error -> {
                    if (error instanceof InvalidGrantException) {
                        return Single.error(error);
                    }
                    LOGGER.debug("Failed to decode JWT for trusted issuer validation: {}", error.getMessage());
                    return Single.error(new InvalidGrantException(
                            "Invalid " + supportedTokenType + ": " + error.getMessage()));
                });
    }

    private ValidatedToken buildValidatedToken(JWTClaimsSet claimsSet, Domain domain,
                                               TrustedIssuer matchingIssuer) {
        long exp = claimsSet.getExpirationTime() != null ? claimsSet.getExpirationTime().getTime() / 1000 : 0;
        long iat = claimsSet.getIssueTime() != null ? claimsSet.getIssueTime().getTime() / 1000 : 0;
        long nbf = claimsSet.getNotBeforeTime() != null ? claimsSet.getNotBeforeTime().getTime() / 1000 : 0;

        TokenValidationUtils.validateTemporalClaims(exp, nbf, supportedTokenType);

        Map<String, Object> claims = new HashMap<>(claimsSet.getClaims());
        Set<String> scopes = applyScopeMapping(TokenValidationUtils.parseScopes(claims.get(Claims.SCOPE)), matchingIssuer);
        List<String> audience = TokenValidationUtils.parseAudience(claims.get(Claims.AUD));

        return TokenValidationUtils.buildValidatedToken(claims,
                exp, iat, nbf,
                scopes, audience,
                supportedTokenType, domain, matchingIssuer);
    }

    private Set<String> applyScopeMapping(Set<String> originalScopes, TrustedIssuer issuer) {
        Map<String, String> mappings = issuer.getScopeMappings();
        if (mappings == null || mappings.isEmpty()) {
            return originalScopes;
        }
        return originalScopes.stream()
                .map(mappings::get)
                .filter(Objects::nonNull)
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
}
