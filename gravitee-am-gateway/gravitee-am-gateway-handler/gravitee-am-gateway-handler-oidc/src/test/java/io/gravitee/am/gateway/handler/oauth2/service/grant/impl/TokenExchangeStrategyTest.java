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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeResult;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;

@ExtendWith(MockitoExtension.class)
class TokenExchangeStrategyTest {

    @Mock
    private TokenExchangeService tokenExchangeService;

    @Mock
    private UserGatewayService userGatewayService;

    private TokenExchangeStrategy strategy;
    private Domain domain;
    private Client client;
    private TokenExchangeSettings tokenExchangeSettings;

    @BeforeEach
    void setUp() {
        strategy = new TokenExchangeStrategy(tokenExchangeService, userGatewayService);

        domain = new Domain();
        domain.setId("domain-id");
        tokenExchangeSettings = new TokenExchangeSettings();
        tokenExchangeSettings.setEnabled(true);
        domain.setTokenExchangeSettings(tokenExchangeSettings);

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.TOKEN_EXCHANGE));
    }

    @Test
    void shouldSupportTokenExchangeGrantType() {
        assertTrue(strategy.supports(GrantType.TOKEN_EXCHANGE, client, domain));
    }

    @Test
    void shouldNotSupportWhenDomainDisabled() {
        tokenExchangeSettings.setEnabled(false);
        assertFalse(strategy.supports(GrantType.TOKEN_EXCHANGE, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.TOKEN_EXCHANGE, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldProcessSuccessfully() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read"));

        User user = new User();
        user.setId("user-id");

        Date expiration = new Date(System.currentTimeMillis() + 3600000);
        TokenExchangeResult exchangeResult = TokenExchangeResult.forImpersonation(
                user,
                TokenTypeHint.ACCESS_TOKEN.name(),
                expiration,
                "subject-token-id",
                TokenTypeHint.ACCESS_TOKEN.name()
        );

        when(tokenExchangeService.exchange(any(), eq(client), eq(domain), eq(userGatewayService)))
                .thenReturn(Single.just(exchangeResult));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.TOKEN_EXCHANGE, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertFalse(result.supportRefreshToken()); // Token exchange doesn't support refresh

        assertInstanceOf(GrantData.TokenExchangeData.class, result.grantData());
        GrantData.TokenExchangeData data = (GrantData.TokenExchangeData) result.grantData();
        assertEquals(TokenTypeHint.ACCESS_TOKEN.name(), data.issuedTokenType());
        assertEquals(expiration, data.expiration());
        assertEquals("subject-token-id", data.subjectTokenId());
        assertEquals(TokenTypeHint.ACCESS_TOKEN.name(), data.subjectTokenType());
        assertFalse(data.isDelegation());
        assertNull(data.actorInfo());
    }

    @Test
    void shouldNotSupportWhenTokenExchangeSettingsNull() {
        domain.setTokenExchangeSettings(null);
        assertFalse(strategy.supports(GrantType.TOKEN_EXCHANGE, client, domain));
    }

    @Test
    void shouldFailWhenTokenExchangeServiceFails() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        when(tokenExchangeService.exchange(any(), eq(client), eq(domain), eq(userGatewayService)))
                .thenReturn(Single.error(new InvalidGrantException("Invalid subject token")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("Invalid subject token"));
    }

    @Test
    void shouldProcessWithIdTokenType() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("openid"));

        User user = new User();
        user.setId("user-id");

        Date expiration = new Date(System.currentTimeMillis() + 3600000);
        TokenExchangeResult exchangeResult = TokenExchangeResult.forImpersonation(
                user,
                TokenTypeHint.ID_TOKEN.name(),
                expiration,
                "subject-token-id",
                TokenTypeHint.ACCESS_TOKEN.name()
        );

        when(tokenExchangeService.exchange(any(), eq(client), eq(domain), eq(userGatewayService)))
                .thenReturn(Single.just(exchangeResult));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        GrantData.TokenExchangeData data = (GrantData.TokenExchangeData) result.grantData();
        assertEquals(TokenTypeHint.ID_TOKEN.name(), data.issuedTokenType());
    }
}
