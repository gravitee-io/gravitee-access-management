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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.SubjectTokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared logic for validating locally issued JWT-based tokens (access, refresh, id).
 */
abstract class AbstractTokenValidator implements SubjectTokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTokenValidator.class);

    @Autowired
    protected JWTService jwtService;

    protected abstract JWTService.TokenType tokenType();

    protected abstract String tokenTypeUrn();

    @Override
    public String getSupportedTokenType() {
        return tokenTypeUrn();
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
        //TODO: For now it validated token only against default certificate.
        // In the future when when Trust issuers are implemented: AM-6298,
        // JWT will be validated against trusted issuers certificates or certificates specified in the token exchange settings
        return jwtService.decodeAndVerify(token, ()-> null, tokenType())
                .map(jwt -> {
                    long currentTime = System.currentTimeMillis() / 1000;

                    if (jwt.getExp() > 0 && jwt.getExp() < currentTime) {
                        throw new InvalidGrantException(tokenTypeUrn() + " has expired");
                    }

                    if (jwt.getNbf() > 0 && jwt.getNbf() > currentTime) {
                        throw new InvalidGrantException(tokenTypeUrn() + " is not yet valid");
                    }

                    Map<String, Object> claims = new HashMap<>();
                    jwt.keySet().forEach(key -> claims.put(key, jwt.get(key)));

                    Set<String> scopes = parseScopes(jwt.get(Claims.SCOPE));
                    List<String> audience = parseAudience(jwt.getAud());

                    return ValidatedToken.builder()
                            .subject(jwt.getSub())
                            .issuer(jwt.getIss())
                            .claims(claims)
                            .scopes(scopes)
                            .expiration(jwt.getExp() > 0 ? new Date(jwt.getExp() * 1000) : null)
                            .issuedAt(jwt.getIat() > 0 ? new Date(jwt.getIat() * 1000) : null)
                            .notBefore(jwt.getNbf() > 0 ? new Date(jwt.getNbf() * 1000) : null)
                            .tokenId(jwt.getJti())
                            .audience(audience)
                            .clientId(jwt.get(Claims.CLIENT_ID) != null ? jwt.get(Claims.CLIENT_ID).toString() : null)
                            .tokenType(tokenTypeUrn())
                            .domain(domain.getId())
                            .build();
                })
                .onErrorResumeNext(error -> {
                    LOGGER.debug("Failed to validate {}: {}", tokenTypeUrn(), error.getMessage());
                    return Single.error(new InvalidGrantException("Invalid " + tokenTypeUrn() + ": " + error.getMessage()));
                });
    }

    private Set<String> parseScopes(Object scopeClaim) {
        return switch (scopeClaim) {
            case null -> Collections.emptySet();
            case String s -> new HashSet<>(Arrays.asList(s.split("\\s+")));
            case List list -> new HashSet<>((List<String>) scopeClaim);
            default -> Collections.emptySet();
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAudience(Object audClaim) {
        return switch (audClaim) {
            case null -> Collections.emptyList();
            case String s -> Collections.singletonList(s);
            case List list -> (List<String>) audClaim;
            default -> Collections.emptyList();
        };
    }
}
