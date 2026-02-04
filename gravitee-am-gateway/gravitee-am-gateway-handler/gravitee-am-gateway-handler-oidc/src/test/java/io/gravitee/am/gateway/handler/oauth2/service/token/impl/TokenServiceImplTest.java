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
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenFacade;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import net.minidev.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.dummies.TestCertificateInfoFactory.createTestCertificateInfo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private IDTokenService idTokenService;

    @InjectMocks
    TokenServiceImpl tokenService;

    @Test
    public void when_access_token_is_not_found_should_be_returned_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void shouldUseTokenExchangeExpirationAndClientIdClaim() {
        // Create OAuth2Request with token exchange fields
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        Date expiration = new Date(System.currentTimeMillis() + 60000);

        // Set token exchange specific fields
        request.setExchangeExpiration(expiration);
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        TestObserver<Token> observer = executeTokenCreation(request, client, user);
        observer.assertValue(token -> {
            assertThat(token.getExpireAt()).isNotNull();
            assertThat(token.getExpireAt().toInstant().getEpochSecond()).isEqualTo(expiration.toInstant().getEpochSecond());
            assertThat(token.getIssuedTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
            return true;
        });

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(1)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        JWT captured = jwtCaptor.getValue();
        assertThat(captured.getExp()).isEqualTo(expiration.toInstant().getEpochSecond());
        // client_id is now always set to request.getClientId() for all tokens
        assertThat(captured.get(Claims.CLIENT_ID)).isEqualTo("test-client");
    }

    @Test
    public void shouldNotExceedClientExpirationWhenSubjectTokenLivesLonger() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        // Subject token expiration two hours in the future, default client expiration is 1 hour
        Date longExpiration = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2));
        request.setExchangeExpiration(longExpiration);

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        TestObserver<Token> observer = executeTokenCreation(request, client, user);
        observer.assertValue(token -> token.getExpireAt() != null);
        Token createdToken = observer.values().get(0);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(1)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        JWT captured = jwtCaptor.getValue();

        long defaultExpiration = captured.getIat() + client.getAccessTokenValiditySeconds();
        long subjectExpiration = request.getExchangeExpiration().toInstant().getEpochSecond();
        long expectedExpiration = Math.min(defaultExpiration, subjectExpiration);

        assertThat(captured.getExp()).isEqualTo(expectedExpiration);
        assertThat(createdToken.getExpireAt().toInstant().getEpochSecond()).isEqualTo(expectedExpiration);
    }

    // ========== Helper Methods ==========

    private void setupDefaultRepositoryMocks() {
        Mockito.when(accessTokenRepository.findByToken(Mockito.anyString())).thenReturn(Maybe.empty());
        Mockito.when(refreshTokenRepository.findByToken(Mockito.anyString())).thenReturn(Maybe.empty());
    }

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
        setupCommonMocks((OAuth2Request) request);
    }

    private void setupCommonMocks(OAuth2Request request) {
        when(openIDDiscoveryService.getIssuer(anyString())).thenReturn("https://auth.example.com");
        when(jwtService.encodeJwt(any(JWT.class), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
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
        verify(jwtService, Mockito.times(2)).encodeJwt(jwtCaptor.capture(), any(Client.class));
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
        return executeTokenCreation((OAuth2Request) request, client, user);
    }

    private TestObserver<Token> executeTokenCreation(OAuth2Request request, Client client, User user) {
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
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));

    }

    @Test
    public void when_none_token_is_found_should_return_empty() {
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_not_found_should_be_return_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token instanceof RefreshToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_not_found_should_be_return_access_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token instanceof AccessToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull())).thenReturn(Maybe.empty());
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
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected to find a ClientTokenAuditBuilder in audit reports"));

        // Verify the audit contains the resource parameter
        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();
        assertThat(auditMessage).contains("resource");
        assertThat(auditMessage).contains("https://mcp.example.com/api/v1");
        assertThat(auditMessage).contains("https://mcp2.example.com/api/v1");
    }

    @Test
    public void when_introspect_access_token_and_access_token_is_not_found_should_use_jwt_aud_as_clientId() {
        // Arrange: JWT with aud claim set to "token-client-id"
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.setAud("token-client-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);
        
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull()))
                .thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull()))
                .thenReturn(Maybe.empty());
        
        // Act: Introspect without callerClientId (null)
        TestObserver<Token> observer = tokenService.introspect("token").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from JWT's aud claim (fallback when repository token not found)
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("token-client-id");
                    return true;
                });
    }

    @Test
    public void when_introspect_access_token_should_use_original_client_id_from_repository() {
        // Arrange: JWT with aud claim set to "resource-id" (RFC 8707), original client ID stored in repository
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.setAud("resource-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);
        
        String originalClientId = "original-client-id";
        String callerClientId = "caller-client-id";
        
        io.gravitee.am.repository.oauth2.model.AccessToken repoToken = 
                new io.gravitee.am.repository.oauth2.model.AccessToken();
        repoToken.setClient(originalClientId);
        
        Mockito.when(accessTokenRepository.findByToken("access-token-id")).thenReturn(Maybe.just(repoToken));
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.empty());
        
        // Act: Introspect with callerClientId (different from original client)
        TestObserver<Token> observer = tokenService.introspect("token", callerClientId).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from repository (original client), not callerClientId
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo(originalClientId);
                    assertThat(token.getClientId()).isNotEqualTo(callerClientId);
                    return true;
                });
    }

    @Test
    public void when_introspect_refresh_token_and_refresh_token_is_not_found_should_use_jwt_aud_as_clientId() {
        // Arrange: JWT refresh token with aud claim set to "token-client-id"
        JWT jwt = new JWT();
        jwt.setJti("refresh-token-id");
        jwt.setAud("token-client-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 7200);
        
        setupDefaultRepositoryMocks();
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.isNull()))
                .thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.isNull()))
                .thenReturn(Maybe.just(jwt));
        
        // Act: Introspect without callerClientId (null) and REFRESH_TOKEN hint
        TestObserver<Token> observer = tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from JWT's aud claim (fallback when repository token not found)
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("token-client-id");
                    assertThat(token).isInstanceOf(RefreshToken.class);
                    return true;
                });
    }

    @Test
    public void when_introspect_refresh_token_should_use_original_client_id_from_repository() {
        // Arrange: JWT refresh token with aud claim set to "resource-id" (RFC 8707), original client ID stored in repository
        JWT jwt = new JWT();
        jwt.setJti("refresh-token-id");
        jwt.setAud("resource-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 7200);

        String originalClientId = "original-client-id";
        String callerClientId = "caller-client-id";

        io.gravitee.am.repository.oauth2.model.RefreshToken repoToken =
                new io.gravitee.am.repository.oauth2.model.RefreshToken();
        repoToken.setClient(originalClientId);

        Mockito.when(refreshTokenRepository.findByToken("refresh-token-id")).thenReturn(Maybe.just(repoToken));
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.anyString(), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.anyString(), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.just(jwt));

        // Act: Introspect with callerClientId (different from original client) and REFRESH_TOKEN hint
        TestObserver<Token> observer = tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN, callerClientId).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        // Assert: Token should have clientId from repository (original client), not callerClientId
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo(originalClientId);
                    assertThat(token.getClientId()).isNotEqualTo(callerClientId);
                    assertThat(token).isInstanceOf(RefreshToken.class);
                    return true;
                });
    }

    private EncodedJWT sampleEncodedJwt() {
        return new EncodedJWT("encoded-jwt", createTestCertificateInfo());
    }

    // ========== Token Exchange ID Token-Only Response Tests ==========

    @Test
    public void shouldCreateIdTokenOnlyResponseWhenIssuedTokenTypeIsIdToken() {
        // Arrange: OAuth2Request for token exchange with ID_TOKEN as issued type
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        request.setScopes(Set.of("openid", "profile"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        client.setIdTokenValiditySeconds(3600);
        User user = createUser("user-123");

        // Setup mocks - only what's needed for ID token path
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(idTokenService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.signature"));

        // Act
        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        // Assert
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(token -> {
            // Verify token_type is "N_A" per RFC 8693 for non-access tokens
            assertThat(token.getTokenType()).isEqualTo("N_A");
            // Verify issued_token_type is ID_TOKEN
            assertThat(token.getIssuedTokenType()).isEqualTo(TokenType.ID_TOKEN);
            // Verify the access_token field contains the ID token
            assertThat(token.getValue()).startsWith("eyJ");
            // Verify no refresh token
            assertThat(token.getRefreshToken()).isNull();
            return true;
        });

        // Verify IDTokenService was called
        verify(idTokenService).create(any(OAuth2Request.class), any(Client.class), any(User.class), any());
        // Verify JWTService.encodeJwt was NOT called (ID tokens created via IDTokenService)
        verify(jwtService, Mockito.never()).encodeJwt(any(JWT.class), any(Client.class));
    }

    @Test
    public void shouldRespectExchangeExpirationForIdTokenOnlyResponse() {
        // Arrange: OAuth2Request with exchange expiration shorter than client default
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        // Subject token expires in 30 seconds
        Date shortExpiration = new Date(System.currentTimeMillis() + 30000);
        request.setExchangeExpiration(shortExpiration);

        Client client = createClient("test-client");
        client.setIdTokenValiditySeconds(3600); // Client default is 1 hour
        User user = createUser("user-123");

        // Setup mocks - only what's needed for ID token path
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(idTokenService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.signature"));

        // Act
        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        // Assert
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(token -> {
            // expires_in should be limited by the subject token expiration (30 seconds)
            // not the client default (3600 seconds)
            assertThat(token.getExpiresIn()).isLessThanOrEqualTo(30);
            assertThat(token.getExpiresIn()).isGreaterThan(0);
            return true;
        });
    }

    @Test
    public void shouldUseClientIdTokenValidityWhenNoExchangeExpiration() {
        // Arrange: OAuth2Request without exchange expiration
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");
        // No exchange expiration set

        Client client = createClient("test-client");
        client.setIdTokenValiditySeconds(1800); // 30 minutes
        User user = createUser("user-123");

        // Setup mocks - only mock what's needed for ID token-only path
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(idTokenService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.signature"));

        // Act
        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        // Assert
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(token -> {
            // expires_in should use client's ID token validity
            assertThat(token.getExpiresIn()).isEqualTo(1800);
            return true;
        });
    }

    @Test
    public void shouldCreateAccessTokenWhenIssuedTokenTypeIsAccessToken() {
        // Arrange: Verify normal token creation path is used when issued type is ACCESS_TOKEN
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        // Act
        TestObserver<Token> observer = executeTokenCreation(request, client, user);

        // Assert
        observer.assertValue(token -> {
            // Token type should be Bearer for access tokens
            assertThat(token.getTokenType().toLowerCase()).isEqualTo("bearer");
            assertThat(token.getIssuedTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
            return true;
        });

        // Verify JWTService.encodeJwt WAS called (normal access token path)
        verify(jwtService, Mockito.atLeastOnce()).encodeJwt(any(JWT.class), any(Client.class));
        // Verify IDTokenService was NOT called
        verify(idTokenService, Mockito.never()).create(any(OAuth2Request.class), any(Client.class), any(User.class), any());
    }

    @Test
    public void shouldNotCreateIdTokenOnlyResponseWhenIssuedTokenTypeIsNull() {
        // Arrange: Normal request without issued token type (non-token-exchange)
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(null); // No issued token type
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        User user = createUser("user-123");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        // Act
        TestObserver<Token> observer = executeTokenCreation(request, client, user);

        // Assert
        observer.assertValue(token -> {
            // Token type should be Bearer for access tokens
            assertThat(token.getTokenType().toLowerCase()).isEqualTo("bearer");
            return true;
        });

        // Verify normal token creation path was used
        verify(jwtService, Mockito.atLeastOnce()).encodeJwt(any(JWT.class), any(Client.class));
        verify(idTokenService, Mockito.never()).create(any(OAuth2Request.class), any(Client.class), any(User.class), any());
    }

}