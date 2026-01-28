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
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.am.service.AuthenticationFlowContextService;
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
import java.util.Set;

import static io.gravitee.am.common.oauth2.CodeChallengeMethod.S256;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationCodeStrategyTest {

    @Mock
    private AuthorizationCodeService authorizationCodeService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Mock
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    private AuthorizationCodeStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        strategy = new AuthorizationCodeStrategy(
                authorizationCodeService,
                userAuthenticationManager,
                authenticationFlowContextService,
                resourceConsistencyValidationService,
                true
        );

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN));
    }

    @Test
    void shouldSupportAuthorizationCodeGrantType() {
        assertTrue(strategy.supports(GrantType.AUTHORIZATION_CODE, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.AUTHORIZATION_CODE, client, domain));
    }

    @Test
    void shouldFailWhenCodeMissing() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidRequestException.class)
                .assertError(e -> e.getMessage().contains("code"));
    }

    @Test
    void shouldFailWhenCodeInvalid() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "invalid-code");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);

        when(authorizationCodeService.remove(eq("invalid-code"), eq(client)))
                .thenReturn(Maybe.empty());

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldProcessSuccessfully() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.REDIRECT_URI, "https://callback.example.com");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.REDIRECT_URI, "https://callback.example.com");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid", "profile"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals(GrantType.AUTHORIZATION_CODE, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertTrue(result.supportRefreshToken());

        assertInstanceOf(GrantData.AuthorizationCodeData.class, result.grantData());
        GrantData.AuthorizationCodeData data = (GrantData.AuthorizationCodeData) result.grantData();
        assertEquals("valid-code", data.code());
        assertEquals("https://callback.example.com", data.redirectUri());
    }

    @Test
    void shouldFailWhenRedirectUriMismatch() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.REDIRECT_URI, "https://different.example.com");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.REDIRECT_URI, "https://callback.example.com");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("mismatch"));
    }

    // ==================== PKCE Tests ====================

    @Test
    void shouldFailWhenCodeVerifierMissingButCodeChallengePresent() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.CODE_CHALLENGE, "challenge-value");
        codeParams.add(Parameters.CODE_CHALLENGE_METHOD, "S256");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("code_verifier"));
    }

    @Test
    void shouldFailWhenCodeVerifierInvalid() {
        // code_verifier must be between 43-128 characters
        String invalidVerifier = "short";

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.CODE_VERIFIER, invalidVerifier);

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.CODE_CHALLENGE, "challenge-value");
        codeParams.add(Parameters.CODE_CHALLENGE_METHOD, "S256");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("code_verifier"));
    }

    @Test
    void shouldFailWhenCodeVerifierDoesNotMatchChallenge() {
        // Valid length verifier but doesn't match challenge
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String wrongChallenge = "wrong-challenge-value";

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.CODE_VERIFIER, codeVerifier);

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.CODE_CHALLENGE, wrongChallenge);
        codeParams.add(Parameters.CODE_CHALLENGE_METHOD, "S256");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("code_verifier"));
    }

    @Test
    void shouldProcessSuccessfullyWithValidPkceS256() {
        // Valid PKCE flow with S256
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String codeChallenge = S256.getChallenge(codeVerifier);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.CODE_VERIFIER, codeVerifier);

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.CODE_CHALLENGE, codeChallenge);
        codeParams.add(Parameters.CODE_CHALLENGE_METHOD, "S256");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals(GrantType.AUTHORIZATION_CODE, result.grantType());
        assertEquals(user, result.resourceOwner());
    }

    @Test
    void shouldProcessSuccessfullyWithValidPkcePlain() {
        // Valid PKCE flow with plain method (code_verifier == code_challenge)
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        parameters.add(Parameters.CODE_VERIFIER, codeVerifier);

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.CODE_CHALLENGE, codeVerifier); // plain: challenge == verifier
        codeParams.add(Parameters.CODE_CHALLENGE_METHOD, "plain");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals(GrantType.AUTHORIZATION_CODE, result.grantType());
    }

    @Test
    void shouldProcessSuccessfullyWithoutPkce() {
        // No PKCE - should succeed when code_challenge was not present in original request
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        // No code_challenge in original request

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals(GrantType.AUTHORIZATION_CODE, result.grantType());
    }

    @Test
    void shouldFailWhenRedirectUriMissingButWasRequiredInAuthorization() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");
        // redirect_uri missing in token request

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();
        codeParams.add(Parameters.REDIRECT_URI, "https://callback.example.com"); // was present in authorization

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("missing") || e.getMessage().contains("Redirect"));
    }

    @Test
    void shouldFailWhenUserNotFound() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.empty()); // User not found

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldNotSupportRefreshTokenWhenClientDoesNotHaveIt() {
        client.setAuthorizedGrantTypes(List.of(GrantType.AUTHORIZATION_CODE)); // No refresh_token

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "valid-code");

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(parameters);

        User user = new User();
        user.setId("user-id");

        MultiValueMap<String, String> codeParams = new LinkedMultiValueMap<>();

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("valid-code");
        authorizationCode.setSubject("user-id");
        authorizationCode.setScopes(Set.of("openid"));
        authorizationCode.setTransactionId("tx-id");
        authorizationCode.setRequestParameters(codeParams);

        when(authorizationCodeService.remove(eq("valid-code"), eq(client)))
                .thenReturn(Maybe.just(authorizationCode));
        when(authenticationFlowContextService.removeContext(anyString(), anyInt()))
                .thenReturn(Single.just(new AuthenticationFlowContext()));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any()))
                .thenReturn(Maybe.just(user));
        when(resourceConsistencyValidationService.resolveFinalResources(any(), any()))
                .thenReturn(Set.of());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertFalse(result.supportRefreshToken());
    }
}
