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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenFacade;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import net.minidev.json.JSONArray;

@RunWith(MockitoJUnitRunner.class)
public class TokenServiceImplTest {

    @Mock
    IntrospectionTokenFacade introspectionTokenFacade;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenEnhancer tokenEnhancer;

    @Mock
    private JWTService jwtService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private AuditService auditService;

    @Mock
    private SubjectManager subjectManager;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @InjectMocks
    TokenServiceImpl tokenService;

    @Test
    public void when_access_token_is_not_found_should_be_returned_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    // ========== Helper Methods ==========

    private AuthorizationRequest createAuthorizationRequest(String clientId, Set<String> resources, Map<String, Object> previousRefreshToken) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setClientId(clientId);
        request.setSupportRefreshToken(true);
        request.setResources(resources);
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setOrigin("https://auth.example.com"); // Set origin so getIssuer mock is used
        if (previousRefreshToken != null) {
            request.setRefreshToken(previousRefreshToken);
        }
        return request;
    }

    private Client createClient(String clientId) {
        Client client = new Client();
        client.setClientId(clientId);
        client.setAccessTokenValiditySeconds(3600);
        client.setRefreshTokenValiditySeconds(7200);
        return client;
    }

    private User createUser(String userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private void setupCommonMocks(AuthorizationRequest request) {
        when(openIDDiscoveryService.getIssuer(anyString())).thenReturn("https://auth.example.com");
        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("encoded-jwt"));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("access-token")));
        when(tokenManager.storeAccessToken(any())).thenReturn(Completable.complete());
        when(tokenManager.storeRefreshToken(any())).thenReturn(Completable.complete());
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
    }

    private Map<String, Object> createPreviousRefreshToken(Set<String> origResources) {
        Map<String, Object> previousRefreshToken = new HashMap<>();
        JSONArray prevOrig = new JSONArray();
        prevOrig.addAll(origResources);
        previousRefreshToken.put("orig_resources", prevOrig);
        return previousRefreshToken;
    }

    private JWT captureRefreshTokenJWT() {
        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(2)).encode(jwtCaptor.capture(), any(Client.class));
        return jwtCaptor.getAllValues().get(1);
    }

    private void verifyRefreshTokenOrigResources(JWT refreshTokenJWT, Set<String> expectedResources, String expectedAudience) {
        if (expectedResources == null || expectedResources.isEmpty()) {
            assertThat(refreshTokenJWT.containsKey("orig_resources")).isFalse();
        } else {
            assertThat(refreshTokenJWT.containsKey("orig_resources")).isTrue();
            Object origClaim = refreshTokenJWT.get("orig_resources");
            assertThat(origClaim).isInstanceOf(JSONArray.class);
            JSONArray origArr = (JSONArray) origClaim;
            assertThat(origArr).containsExactlyInAnyOrderElementsOf(expectedResources);
        }
        assertThat(refreshTokenJWT.getAud()).isEqualTo(expectedAudience);
    }

    private TestObserver<Token> executeTokenCreation(AuthorizationRequest request, Client client, User user) {
        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        return observer;
    }

    // ========== Test Cases ==========

    @Test
    public void when_rotating_refresh_token_should_preserve_orig_resources_from_previous_token() {
        // Arrange: Previous refresh token had two resources (original grant)
        Set<String> originalResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");
        Set<String> requestedResources = Set.of("https://api.example.com/photos");

        AuthorizationRequest request = createAuthorizationRequest("test-client", requestedResources, 
                createPreviousRefreshToken(originalResources));
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);

        // Act
        executeTokenCreation(request, client, user);

        // Assert: orig_resources must be preserved from previous token (not from requested subset)
        JWT newRefreshJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(newRefreshJWT, originalResources, "test-client");
    }

    @Test
    public void when_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));

    }

    @Test
    public void when_none_token_is_found_should_return_empty() {
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_not_found_should_be_return_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token instanceof RefreshToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_not_found_should_be_return_access_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token instanceof AccessToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void when_creating_tokens_with_authorization_resources_should_store_orig_resources_in_refresh_token() {
        // Arrange: Auth code with multiple resources (original grant)
        Set<String> authCodeResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");
        
        AuthorizationRequest authRequest = createAuthorizationRequest("test-client", authCodeResources, null);
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(authRequest);
        
        // Act: Create tokens
        executeTokenCreation(authRequest, client, user);
        
        // Assert: Verify refresh token contains orig_resources claim
        JWT refreshTokenJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(refreshTokenJWT, authCodeResources, "test-client");
    }

    @Test
    public void when_refresh_token_rotation_with_null_previous_token_should_fallback_to_request_resources() {
        // Arrange: Previous refresh token is null
        Set<String> requestedResources = Set.of("https://api.example.com/photos");
        
        AuthorizationRequest request = createAuthorizationRequest("test-client", requestedResources, null);
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        
        // Act
        executeTokenCreation(request, client, user);
        
        // Assert: Should fallback to request resources
        JWT newRefreshJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(newRefreshJWT, requestedResources, "test-client");
    }

    @Test
    public void when_refresh_token_rotation_with_no_orig_resources_should_fallback_to_request_resources() {
        // Arrange: Previous refresh token exists but has no orig_resources claim
        // Note: This tests fallback behavior during token creation. In practice, ResourceConsistencyValidationService
        // should validate that requested resources are a subset of original resources before reaching this point.
        Set<String> requestedResources = Set.of("https://api.example.com/photos");
        Map<String, Object> previousRefreshToken = new HashMap<>(); // No orig_resources
        
        AuthorizationRequest request = createAuthorizationRequest("test-client", requestedResources, previousRefreshToken);
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        
        // Act
        executeTokenCreation(request, client, user);
        
        // Assert: Should fallback to request resources when orig_resources is missing from previous token
        JWT newRefreshJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(newRefreshJWT, requestedResources, "test-client");
    }

    @Test
    public void when_refresh_token_rotation_with_no_resources_should_not_store_orig_resources() {
        // Arrange: No resources in request and no orig_resources in previous token
        Map<String, Object> previousRefreshToken = new HashMap<>(); // No orig_resources
        
        AuthorizationRequest request = createAuthorizationRequest("test-client", null, previousRefreshToken);
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        
        // Act
        executeTokenCreation(request, client, user);
        
        // Assert: Should not have orig_resources claim
        JWT newRefreshJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(newRefreshJWT, null, "test-client");
    }

    @Test
    public void when_refresh_token_rotation_with_empty_resources_should_not_store_orig_resources() {
        // Arrange: Empty resources set in request and no orig_resources in previous token
        Map<String, Object> previousRefreshToken = new HashMap<>(); // No orig_resources
        
        AuthorizationRequest request = createAuthorizationRequest("test-client", Set.of(), previousRefreshToken);
        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        
        // Act
        executeTokenCreation(request, client, user);
        
        // Assert: Should not have orig_resources claim
        JWT newRefreshJWT = captureRefreshTokenJWT();
        verifyRefreshTokenOrigResources(newRefreshJWT, Set.of(), "test-client");
    }

    @Test
    public void when_creating_token_with_resources_should_include_resources_in_audit_log() {
        // Request with MCP resource server URIs
        Set<String> mcpResources = Set.of("https://mcp.example.com/api/v1", "https://mcp2.example.com/api/v1");

        AuthorizationRequest request = createAuthorizationRequest("mcp-client", mcpResources, null);
        Client client = createClient("mcp-client");
        client.setDomain("test-domain");
        User user = createUser("user-456");
        setupCommonMocks(request);

        executeTokenCreation(request, client, user);

        // Verify audit service was called with the resource parameters
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());

        // Get the successful audit report (not the error one)
        AuditBuilder capturedBuilder = auditCaptor.getAllValues().stream()
                .filter(builder -> builder instanceof ClientTokenAuditBuilder)
                .findFirst()
                .orElse(null);

        assertThat(capturedBuilder).isNotNull();

        // Verify the audit contains the resource parameter
        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();
        assertThat(auditMessage).contains("resource");
        assertThat(auditMessage).contains("https://mcp.example.com/api/v1");
        assertThat(auditMessage).contains("https://mcp2.example.com/api/v1");
    }

}