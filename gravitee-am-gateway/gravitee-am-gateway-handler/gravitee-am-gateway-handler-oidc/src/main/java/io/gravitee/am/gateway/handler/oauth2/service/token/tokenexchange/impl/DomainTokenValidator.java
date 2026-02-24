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

import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DomainTokenValidator implements TokenValidator {
    private final DefaultTokenValidator defaultTokenValidator;

    private final TokenRepository tokenRepository;
    private final JWTService.TokenType jwtTokenType;
    private final String supportedTokenType;

    public DomainTokenValidator(DefaultTokenValidator defaultTokenValidator,
                                TokenRepository tokenRepository,
                                JWTService.TokenType jwtTokenType,
                                String supportedTokenType) {
        this.defaultTokenValidator = defaultTokenValidator;
        this.tokenRepository = tokenRepository;
        this.jwtTokenType = jwtTokenType;
        this.supportedTokenType = supportedTokenType;
    }

    @Override
    public String getSupportedTokenType() {
        return supportedTokenType;
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
        return defaultTokenValidator.validate(token, settings, domain)
                .concatMap(t -> testIfNotRevoked(t, domain));
    }

    private Single<ValidatedToken> testIfNotRevoked(ValidatedToken token, Domain domain) {
        if(token.getDomain() == null || !token.getDomain().equals(domain.getId())) {
            return Single.just(token);
        } else {
            Maybe<? extends Token> databaseToken = switch (jwtTokenType) {
                case ACCESS_TOKEN -> tokenRepository.findAccessTokenByJti(token.getTokenId());
                case REFRESH_TOKEN -> tokenRepository.findRefreshTokenByJti(token.getTokenId());
                default -> Maybe.empty();
            };
            return databaseToken
                    .concatMapSingle(t -> Single.just(token))
                    .switchIfEmpty(Single.error(new InvalidGrantException("token has been revoked")));
        }
    }
}
