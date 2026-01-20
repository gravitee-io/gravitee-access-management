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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordStrategyTest {

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    private PasswordStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        strategy = new PasswordStrategy(userAuthenticationManager);
        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.PASSWORD, GrantType.REFRESH_TOKEN));
    }

    @Test
    void shouldSupportPasswordGrantType() {
        assertTrue(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.AUTHORIZATION_CODE, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldFailWhenUsernameMissing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.PASSWORD, "secret");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class)
                .assertError(e -> e.getMessage().contains("username"));
    }

    @Test
    void shouldFailWhenPasswordMissing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.USERNAME, "user");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class)
                .assertError(e -> e.getMessage().contains("password"));
    }

    @Test
    void shouldFailWhenAuthenticationFails() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.USERNAME, "user");
        parameters.add(Parameters.PASSWORD, "wrong");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        when(userAuthenticationManager.authenticate(eq(client), any()))
                .thenReturn(Single.error(new RuntimeException("Bad credentials")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldProcessSuccessfully() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.USERNAME, "user");
        parameters.add(Parameters.PASSWORD, "secret");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");
        user.setUsername("user");

        when(userAuthenticationManager.authenticate(eq(client), any()))
                .thenReturn(Single.just(user));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.PASSWORD, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertTrue(result.supportRefreshToken());
        assertInstanceOf(GrantData.PasswordData.class, result.grantData());
        assertEquals("user", ((GrantData.PasswordData) result.grantData()).username());
    }
}
