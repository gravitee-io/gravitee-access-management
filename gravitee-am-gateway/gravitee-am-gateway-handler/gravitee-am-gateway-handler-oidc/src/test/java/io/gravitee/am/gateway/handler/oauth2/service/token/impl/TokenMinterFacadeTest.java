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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenMinterFacadeTest {

    @Mock
    private AccessTokenMinter accessTokenMinter;

    @Mock
    private IdTokenOnlyMinter idTokenOnlyMinter;

    @Mock
    private IdJagTokenMinter idJagTokenMinter;

    @InjectMocks
    private TokenMinterFacade tokenMinterFacade;

    private final OAuth2Request request = new OAuth2Request();
    private final Client client = new Client();
    private final User user = new User();
    private final Token token = new AccessToken("token-value");

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {TokenType.ACCESS_TOKEN, TokenType.JWT})
    void shouldMintAccessTokenWhenNoIdTokenNorIdJagIsRequested(String issuedTokenType) {
        request.setIssuedTokenType(issuedTokenType);
        when(accessTokenMinter.mint(request, client, user)).thenReturn(Single.just(token));

        tokenMinterFacade.mint(request, client, user).test().assertValue(token);

        verifyNoInteractions(idTokenOnlyMinter, idJagTokenMinter);
    }

    @Test
    void shouldMintIdTokenOnlyWhenIdTokenIsRequested() {
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        when(idTokenOnlyMinter.mint(request, client, user)).thenReturn(Single.just(token));

        tokenMinterFacade.mint(request, client, user).test().assertValue(token);

        verifyNoInteractions(accessTokenMinter, idJagTokenMinter);
    }

    @Test
    void shouldMintIdJagWhenIdJagIsRequested() {
        request.setIssuedTokenType(TokenType.ID_JAG);
        when(idJagTokenMinter.mint(request, client, user)).thenReturn(Single.just(token));

        tokenMinterFacade.mint(request, client, user).test().assertValue(token);

        verifyNoInteractions(accessTokenMinter, idTokenOnlyMinter);
    }
}
