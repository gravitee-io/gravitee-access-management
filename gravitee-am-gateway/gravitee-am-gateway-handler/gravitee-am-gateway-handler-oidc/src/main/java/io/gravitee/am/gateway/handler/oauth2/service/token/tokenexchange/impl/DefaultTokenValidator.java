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
import io.gravitee.am.gateway.handler.oauth2.exception.TokenVerificationException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default validator for locally issued JWT-based tokens (access, refresh, id, ...).
 *
 * @author GraviteeSource Team
 */
public class DefaultTokenValidator implements TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTokenValidator.class);

    private final JWTService jwtService;
    private final JWTService.TokenType jwtTokenType;
    private final String supportedTokenType;

    public DefaultTokenValidator(JWTService jwtService,
                                 JWTService.TokenType jwtTokenType,
                                 String supportedTokenType) {
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
        return jwtService.decodeAndVerify(token, () -> null, jwtTokenType)
                .map(jwt -> {
                    TokenValidationUtils.validateTemporalClaims(jwt.getExp(), jwt.getNbf(), supportedTokenType);

                    Map<String, Object> claims = new HashMap<>();
                    jwt.keySet().forEach(key -> claims.put(key, jwt.get(key)));

                    Set<String> scopes = TokenValidationUtils.parseScopes(jwt.get(Claims.SCOPE));
                    List<String> audience = TokenValidationUtils.parseAudience(jwt.getAud());

                    return TokenValidationUtils.buildValidatedToken(claims,
                            jwt.getExp(), jwt.getIat(), jwt.getNbf(),
                            scopes, audience,
                            supportedTokenType, domain, null);
                })
                .onErrorResumeNext(error -> {
                    if (error instanceof InvalidGrantException) {
                        return Single.error(error);
                    }
                    LOGGER.debug("Failed to validate {}: {}", supportedTokenType, error.getMessage());
                    return Single.error(new TokenVerificationException(
                            "Invalid " + supportedTokenType + ": " + error.getMessage()));
                });
    }
}
