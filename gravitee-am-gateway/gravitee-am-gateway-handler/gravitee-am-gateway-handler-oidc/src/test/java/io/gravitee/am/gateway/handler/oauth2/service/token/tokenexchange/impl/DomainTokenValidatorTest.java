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

import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainTokenValidatorTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String ANOTHER_DOMAIN_ID = "another-domain-id";
    private static final String TOKEN_VALUE = "subject-token-value";
    private static final String SUPPORTED_TOKEN_TYPE = TokenType.ACCESS_TOKEN;

    @Mock
    private DefaultTokenValidator defaultTokenValidator;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private TokenExchangeSettings settings;

    private Domain domain;

    @BeforeEach
    void setUp() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
    }

    @Test
    void shouldSkipRepositoryCheckWhenValidatedTokenDomainIsNull() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ACCESS_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, null);
        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(token -> token == validatedToken);

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void shouldSkipRepositoryCheckWhenValidatedTokenDomainDiffers() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ACCESS_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, ANOTHER_DOMAIN_ID);
        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(token -> token == validatedToken);

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void shouldValidateAccessTokenWhenPresentInRepositoryForDomain() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ACCESS_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, DOMAIN_ID);
        AccessToken storedToken = new AccessToken();
        storedToken.setDomain(DOMAIN_ID);

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));
        when(tokenRepository.findAccessTokenByJti(TOKEN_VALUE)).thenReturn(Maybe.just(storedToken));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(token -> token == validatedToken);

        verify(tokenRepository).findAccessTokenByJti(TOKEN_VALUE);
        verify(tokenRepository, never()).findRefreshTokenByJti(TOKEN_VALUE);
    }

    @Test
    void shouldFailWhenAccessTokenIsMissingFromRepository() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ACCESS_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, DOMAIN_ID);

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));
        when(tokenRepository.findAccessTokenByJti(TOKEN_VALUE)).thenReturn(Maybe.empty());

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertNotComplete()
                .assertError(error -> error instanceof InvalidGrantException && error.getMessage().contains("token has been revoked"));

        verify(tokenRepository).findAccessTokenByJti(TOKEN_VALUE);
    }


    @Test
    void shouldValidateRefreshTokenWhenPresentInRepositoryForDomain() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.REFRESH_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, DOMAIN_ID);
        RefreshToken storedToken = new RefreshToken();
        storedToken.setDomain(DOMAIN_ID);

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));
        when(tokenRepository.findRefreshTokenByJti(TOKEN_VALUE)).thenReturn(Maybe.just(storedToken));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(token -> token == validatedToken);

        verify(tokenRepository).findRefreshTokenByJti(TOKEN_VALUE);
        verify(tokenRepository, never()).findAccessTokenByJti(TOKEN_VALUE);
    }

    @Test
    void shouldFailWhenRefreshTokenIsMissingFromRepository() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.REFRESH_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, DOMAIN_ID);

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));
        when(tokenRepository.findRefreshTokenByJti(TOKEN_VALUE)).thenReturn(Maybe.empty());

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertNotComplete()
                .assertError(error -> error instanceof InvalidGrantException && error.getMessage().contains("token has been revoked"));

        verify(tokenRepository).findRefreshTokenByJti(TOKEN_VALUE);
    }

    @Test
    void shouldFailForUnsupportedJwtTokenTypeWhenDomainMatches() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ID_TOKEN);
        ValidatedToken validatedToken = validatedToken(TOKEN_VALUE, DOMAIN_ID);

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.just(validatedToken));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertNotComplete()
                .assertError(error -> error instanceof InvalidGrantException && error.getMessage().contains("token has been revoked"));

        verify(tokenRepository, never()).findAccessTokenByJti(TOKEN_VALUE);
        verify(tokenRepository, never()).findRefreshTokenByJti(TOKEN_VALUE);
    }

    @Test
    void shouldPropagateDefaultValidatorError() {
        DomainTokenValidator validator = createValidator(JWTService.TokenType.ACCESS_TOKEN);
        InvalidGrantException validationError = new InvalidGrantException("validation failed");

        when(defaultTokenValidator.validate(TOKEN_VALUE, settings, domain)).thenReturn(Single.error(validationError));

        validator.validate(TOKEN_VALUE, settings, domain)
                .test()
                .assertNotComplete()
                .assertError(error -> error == validationError);

        verifyNoInteractions(tokenRepository);
    }

    private DomainTokenValidator createValidator(JWTService.TokenType tokenType) {
        return new DomainTokenValidator(defaultTokenValidator, tokenRepository, tokenType, SUPPORTED_TOKEN_TYPE);
    }

    private static ValidatedToken validatedToken(String tokenId, String tokenDomain) {
        return ValidatedToken.builder()
                .tokenId(tokenId)
                .domain(tokenDomain)
                .build();
    }
}
