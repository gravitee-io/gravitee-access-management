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
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClientCredentialsStrategyTest {

    private ClientCredentialsStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        strategy = new ClientCredentialsStrategy();
        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
    }

    @Test
    void shouldSupportClientCredentialsGrantType() {
        assertTrue(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.AUTHORIZATION_CODE, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
        assertFalse(strategy.supports(GrantType.REFRESH_TOKEN, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.AUTHORIZATION_CODE));
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
    }

    @Test
    void shouldProcessRequest() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read", "write"));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.CLIENT_CREDENTIALS, result.grantType());
        assertEquals(Set.of("read", "write"), result.scopes());
        assertNull(result.resourceOwner()); // No user for client credentials
        assertFalse(result.supportRefreshToken()); // No refresh token for client credentials
        assertInstanceOf(GrantData.ClientCredentialsData.class, result.grantData());
    }
}
