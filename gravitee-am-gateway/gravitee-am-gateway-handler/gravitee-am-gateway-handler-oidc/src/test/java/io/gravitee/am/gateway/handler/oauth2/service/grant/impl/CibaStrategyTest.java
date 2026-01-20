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

import io.gravitee.am.common.ciba.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationRejectedException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.oidc.Parameters.ACR_VALUES;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class CibaStrategyTest {

    @Mock
    private AuthenticationRequestService authenticationRequestService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    private CibaStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        strategy = new CibaStrategy(authenticationRequestService, userAuthenticationManager);

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.CIBA_GRANT_TYPE, GrantType.REFRESH_TOKEN));
    }

    @Test
    void shouldSupportCibaGrantType() {
        assertTrue(strategy.supports(GrantType.CIBA_GRANT_TYPE, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.CIBA_GRANT_TYPE, client, domain));
    }

    @Test
    void shouldFailWhenAuthReqIdMissing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class)
                .assertError(e -> e.getMessage().contains("auth_req_id"));
    }

    @Test
    void shouldFailWhenAuthReqIdUnknown() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "unknown_req_id");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        when(authenticationRequestService.retrieve(eq(domain), eq("unknown_req_id"), eq(client)))
                .thenReturn(Single.error(new AuthorizationRejectedException("unknown_req_id")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(AuthorizationRejectedException.class);
    }

    @Test
    void shouldFailWhenSlowDown() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "slow_down");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        when(authenticationRequestService.retrieve(eq(domain), eq("slow_down"), eq(client)))
                .thenReturn(Single.error(new SlowDownException()));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(SlowDownException.class);
    }

    @Test
    void shouldFailWhenClientIdMismatch() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "valid-auth-req-id");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setClientId("different-client-id"); // Different client
        cibaRequest.setSubject("user-id");
        cibaRequest.setScopes(Set.of("openid"));

        when(authenticationRequestService.retrieve(eq(domain), eq("valid-auth-req-id"), eq(client)))
                .thenReturn(Single.just(cibaRequest));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(err -> err.getMessage().contains("not found"));
    }

    @Test
    void shouldProcessSuccessfully() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "valid-auth-req-id");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setClientId("client-id");
        cibaRequest.setSubject("user-id");
        cibaRequest.setScopes(Set.of("openid", "profile"));

        when(authenticationRequestService.retrieve(eq(domain), eq("valid-auth-req-id"), eq(client)))
                .thenReturn(Single.just(cibaRequest));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.CIBA_GRANT_TYPE, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertTrue(result.supportRefreshToken());

        assertInstanceOf(GrantData.CibaData.class, result.grantData());
        GrantData.CibaData data = (GrantData.CibaData) result.grantData();
        assertEquals("valid-auth-req-id", data.authReqId());
    }

    @Test
    void shouldProcessSuccessfullyWithAcrValues() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "valid-auth-req-id");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setClientId("client-id");
        cibaRequest.setSubject("user-id");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setExternalInformation(Map.of(ACR_VALUES, List.of("urn:mace:incommon:iap:silver")));

        when(authenticationRequestService.retrieve(eq(domain), eq("valid-auth-req-id"), eq(client)))
                .thenReturn(Single.just(cibaRequest));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertInstanceOf(GrantData.CibaData.class, result.grantData());
        GrantData.CibaData data = (GrantData.CibaData) result.grantData();
        assertNotNull(data.acrValues());
        assertEquals(1, data.acrValues().size());
        assertEquals("urn:mace:incommon:iap:silver", data.acrValues().get(0));
    }

    @Test
    void shouldNotSupportRefreshWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CIBA_GRANT_TYPE)); // No refresh token

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.AUTH_REQ_ID, "valid-auth-req-id");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setClientId("client-id");
        cibaRequest.setSubject("user-id");
        cibaRequest.setScopes(Set.of("openid"));

        when(authenticationRequestService.retrieve(eq(domain), eq("valid-auth-req-id"), eq(client)))
                .thenReturn(Single.just(cibaRequest));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertFalse(result.supportRefreshToken());
    }
}
