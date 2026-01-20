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
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStrategyTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    private RefreshTokenStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        strategy = new RefreshTokenStrategy(tokenService, userAuthenticationManager, resourceConsistencyValidationService);

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.REFRESH_TOKEN));
    }

    @Test
    void shouldSupportRefreshTokenGrantType() {
        assertTrue(strategy.supports(GrantType.REFRESH_TOKEN, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.REFRESH_TOKEN, client, domain));
    }

    @Test
    void shouldFailWhenRefreshTokenMissing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class);
    }

    @Test
    void shouldProcessSuccessfullyWithUser() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("sub", "user-id");

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject("user-id");
        refreshToken.setScope("read write");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.REFRESH_TOKEN, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertTrue(result.supportRefreshToken()); // Rotation enabled by default

        assertInstanceOf(GrantData.RefreshTokenData.class, result.grantData());
        GrantData.RefreshTokenData data = (GrantData.RefreshTokenData) result.grantData();
        assertEquals("refresh-token-value", data.refreshToken());
    }

    @Test
    void shouldProcessSuccessfullyWithoutUser() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        Map<String, Object> additionalInfo = new HashMap<>();

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject(null); // No user
        refreshToken.setScope("read write");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNull(result.resourceOwner());
    }

    @Test
    void shouldNotSupportRefreshWhenRotationDisabled() {
        client.setDisableRefreshTokenRotation(true);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        Map<String, Object> additionalInfo = new HashMap<>();

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject(null);
        refreshToken.setScope("read");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertFalse(result.supportRefreshToken());
    }

    // ==================== Additional Tests ====================

    @Test
    void shouldFailWhenRefreshTokenInvalid() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "invalid-refresh-token");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        when(tokenService.refresh(eq("invalid-refresh-token"), any(), eq(client)))
                .thenReturn(Single.error(new InvalidGrantException("Refresh token is invalid or expired")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("invalid") || e.getMessage().contains("expired"));
    }

    @Test
    void shouldFailWhenRefreshTokenExpired() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "expired-refresh-token");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        when(tokenService.refresh(eq("expired-refresh-token"), any(), eq(client)))
                .thenReturn(Single.error(new InvalidGrantException("Refresh token has expired")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldFailWhenRefreshTokenBelongsToDifferentClient() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.error(new InvalidGrantException("Refresh token was issued to another client")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("client"));
    }

    @Test
    void shouldProcessWithScopeNarrowing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read")); // Request narrower scope
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("sub", "user-id");

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject("user-id");
        refreshToken.setScope("read write"); // Original token had wider scope
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        // Scopes should be narrowed to what was requested
        assertEquals(Set.of("read"), result.scopes());
    }

    @Test
    void shouldPreserveOriginalResourcesFromRefreshToken() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("resources", Set.of("https://api.example.com"));

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject(null);
        refreshToken.setScope("read");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of("https://api.example.com"));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertInstanceOf(GrantData.RefreshTokenData.class, result.grantData());
    }

    @Test
    void shouldFailWhenUserNotFoundButRequired() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("sub", "user-id");

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject("user-id"); // Token has subject but user not found
        refreshToken.setScope("read");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any()))
                .thenReturn(Maybe.error(new InvalidGrantException("User not found"))); // User lookup fails

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldHandleEmptyRefreshTokenValue() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class);
    }

    @Test
    void shouldPreserveDecodedRefreshTokenData() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REFRESH_TOKEN, "refresh-token-value");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("sub", "user-id");
        additionalInfo.put("custom_claim", "custom_value");

        Token refreshToken = new AccessToken("token-value");
        refreshToken.setSubject(null);
        refreshToken.setScope("read");
        refreshToken.setClientId("client-id");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 3600000));
        refreshToken.setAdditionalInformation(additionalInfo);

        when(tokenService.refresh(eq("refresh-token-value"), any(), eq(client)))
                .thenReturn(Single.just(refreshToken));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertInstanceOf(GrantData.RefreshTokenData.class, result.grantData());
        GrantData.RefreshTokenData data = (GrantData.RefreshTokenData) result.grantData();
        assertNotNull(data.decodedRefreshToken());
        assertEquals("custom_value", data.decodedRefreshToken().get("custom_claim"));
    }
}
