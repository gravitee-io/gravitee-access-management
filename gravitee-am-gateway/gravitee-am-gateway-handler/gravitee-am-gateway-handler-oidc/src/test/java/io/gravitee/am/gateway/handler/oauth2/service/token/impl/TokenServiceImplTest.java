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
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.IdJagTarget;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJag;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJagService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionResult;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionAnyTokenService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.utils.ConstantKeys.BEARER_AUTH_SCHEME;
import static io.gravitee.am.common.utils.ConstantKeys.DPOP_AUTH_SCHEME;
import static io.gravitee.am.gateway.handler.dummies.TestCertificateInfoFactory.createTestCertificateInfo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TokenServiceImplTest {

    @Mock
    IntrospectionAnyTokenService introspectionTokenService;

    @Mock
    private BackwardCompatibleTokenRepository tokenRepository;

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

    @Mock
    private IdJagService idJagService;

    @InjectMocks
    TokenServiceImpl tokenService;

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

    @Test
    public void shouldBindAccessTokenToDpopKeyWhenConfirmationMethodJktPresent() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");
        request.setConfirmationMethodJkt("the-jwk-thumbprint");

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        TestObserver<Token> observer = executeTokenCreation(request, client, user);
        observer.assertValue(token -> DPOP_AUTH_SCHEME.equals(token.getTokenType()));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat((Map<String, Object>) jwtCaptor.getValue().getConfirmationMethod())
                .containsEntry(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT, "the-jwk-thumbprint")
                .doesNotContainKey(JWT.CONFIRMATION_METHOD_X509_THUMBPRINT);
    }

    @Test
    public void shouldRecordDpopJktInTokenCreatedAudit() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");
        request.setConfirmationMethodJkt("the-jwk-thumbprint");

        Client client = createClient("test-client");
        client.setDomain("test-domain");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        executeTokenCreation(request, client, user);

        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());
        AuditBuilder capturedBuilder = auditCaptor.getAllValues().stream()
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected to find a ClientTokenAuditBuilder in audit reports"));

        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();
        assertThat(auditMessage).contains("DPOP_JKT");
        assertThat(auditMessage).contains("the-jwk-thumbprint");
    }

    @Test
    public void shouldBindRefreshTokenToDpopKeyWhenConfirmationMethodJktPresent() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(true);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");
        request.setConfirmationMethodJkt("the-jwk-thumbprint");

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        executeTokenCreation(request, client, user);

        ArgumentCaptor<io.gravitee.am.repository.oauth2.model.RefreshToken> captor =
                ArgumentCaptor.forClass(io.gravitee.am.repository.oauth2.model.RefreshToken.class);
        verify(tokenManager).storeTokens(any(), captor.capture());
        assertThat(captor.getValue().getJkt()).isEqualTo("the-jwk-thumbprint");

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(2)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getAllValues())
                .allSatisfy(encoded -> assertThat((Map<String, Object>) encoded.getConfirmationMethod())
                        .containsEntry(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT, "the-jwk-thumbprint"));
    }

    @Test
    public void shouldNotBindRefreshTokenWhenNoConfirmationMethodJkt() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(true);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        executeTokenCreation(request, client, user);

        ArgumentCaptor<io.gravitee.am.repository.oauth2.model.RefreshToken> captor =
                ArgumentCaptor.forClass(io.gravitee.am.repository.oauth2.model.RefreshToken.class);
        verify(tokenManager).storeTokens(any(), captor.capture());
        assertThat(captor.getValue().getJkt()).isNull();
    }

    @Test
    public void shouldResolveStoredRefreshTokenJkt_whenBound() {
        Client client = createClient("test-client");
        JWT jwt = new JWT();
        jwt.setJti("rt-jti");
        jwt.setConfirmationMethod(Map.of(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT, "the-jkt"));
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(jwt));

        tokenService.getRefreshTokenJkt("rt", client).test()
                .assertValue("the-jkt").assertComplete();
    }

    @Test
    public void shouldResolveEmptyJkt_whenRefreshTokenUnbound() {
        Client client = createClient("test-client");
        JWT jwt = new JWT();
        jwt.setJti("rt-jti");
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(jwt));

        tokenService.getRefreshTokenJkt("rt", client).test()
                .assertNoValues().assertComplete();
    }

    @Test
    public void shouldResolveEmptyJkt_whenRefreshTokenUndecodable() {
        Client client = createClient("test-client");
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any()))
                .thenReturn(Single.error(new RuntimeException("invalid token")));

        tokenService.getRefreshTokenJkt("bad-rt", client).test()
                .assertNoValues().assertNoErrors().assertComplete();
    }

    @Test
    public void shouldKeepCertificateBoundTokenAsBearer() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("test-client");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");
        request.setConfirmationMethodX5S256("the-cert-thumbprint");

        Client client = createClient("test-client");
        User user = createUser("user");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        TestObserver<Token> observer = executeTokenCreation(request, client, user);
        observer.assertValue(token -> BEARER_AUTH_SCHEME.toLowerCase().equals(token.getTokenType()));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat((Map<String, Object>) jwtCaptor.getValue().getConfirmationMethod())
                .containsEntry(JWT.CONFIRMATION_METHOD_X509_THUMBPRINT, "the-cert-thumbprint")
                .doesNotContainKey(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT);
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
        setupCommonMocks((OAuth2Request) request);
    }

    private void setupCommonMocks(OAuth2Request request) {
        when(openIDDiscoveryService.getIssuer(anyString())).thenReturn("https://auth.example.com");
        when(jwtService.encodeJwt(any(JWT.class), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("access-token")));
        when(tokenManager.storeTokens(any(), any())).thenReturn(Completable.complete());
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
        jwt.setAud("jwt-aud");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.ACCESS_TOKEN)));
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id") && "client-id".equals(token.getClientId()));
    }

    @Test
    public void shouldReturnStoredRefreshTokenWithoutHint() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.REFRESH_TOKEN)));
        tokenService.introspect("token").test()
                .assertValue(token -> token instanceof RefreshToken && token.getValue().equals("id"));
        Mockito.verify(introspectionTokenService, Mockito.times(1)).introspect(Mockito.anyString(), Mockito.any(), Mockito.any());
    }

    @Test
    public void when_none_token_is_found_should_return_empty() {
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void shouldReturnEmptyWhenTokenIsInvalid() {
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.error(new InvalidTokenException("The token is invalid")));
        tokenService.introspect("token").test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.ACCESS_TOKEN)));
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void shouldReturnStoredRefreshTokenWhenHintIsAccessToken() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.REFRESH_TOKEN)));
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token instanceof RefreshToken && token.getValue().equals("id"));
    }

    @Test
    public void shouldReturnEmptyWhenTokenIsNotFoundWithAccessTokenHint() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertComplete()
                .assertNoValues();
        Mockito.verify(introspectionTokenService, Mockito.times(1)).introspect(Mockito.anyString(), Mockito.any(), Mockito.any());
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.REFRESH_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.REFRESH_TOKEN)));
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void shouldReturnStoredAccessTokenWhenHintIsRefreshToken() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.REFRESH_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, "client-id", JWTService.TokenType.ACCESS_TOKEN)));
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token instanceof AccessToken && token.getValue().equals("id"));
    }

    @Test
    public void shouldReturnEmptyWhenTokenIsNotFoundWithRefreshTokenHint() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.REFRESH_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.empty());
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
        assertThat(auditMessage).contains("RESOURCE");
        assertThat(auditMessage).contains("https://mcp.example.com/api/v1");
        assertThat(auditMessage).contains("https://mcp2.example.com/api/v1");
    }

    @Test
    public void when_introspect_access_token_and_original_client_id_is_not_found_should_use_jwt_aud_as_clientId() {
        // Arrange: JWT with aud claim set to "token-client-id"
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.setAud("token-client-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, null, JWTService.TokenType.ACCESS_TOKEN)));
        
        // Act: Introspect without callerClientId (null)
        TestObserver<Token> observer = tokenService.introspect("token").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from JWT's aud claim
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("token-client-id");
                    return true;
                });
    }

    @Test
    public void when_introspect_access_token_should_use_original_client_id() {
        // Arrange: JWT with aud claim set to "resource-id" (RFC 8707)
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.setAud("resource-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);
        
        String originalClientId = "original-client-id";
        String callerClientId = "caller-client-id";

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, originalClientId, JWTService.TokenType.ACCESS_TOKEN)));
        
        // Act: Introspect with callerClientId (different from original client)
        TestObserver<Token> observer = tokenService.introspect("token", callerClientId).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from introspection result (original client), not callerClientId
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo(originalClientId);
                    assertThat(token.getClientId()).isNotEqualTo(callerClientId);
                    return true;
                });
    }

    @Test
    public void when_introspect_refresh_token_and_original_client_id_is_not_found_should_use_jwt_aud_as_clientId() {
        // Arrange: JWT refresh token with aud claim set to "token-client-id"
        JWT jwt = new JWT();
        jwt.setJti("refresh-token-id");
        jwt.setAud("token-client-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 7200);

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.REFRESH_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, null, JWTService.TokenType.REFRESH_TOKEN)));
        
        // Act: Introspect without callerClientId (null) and REFRESH_TOKEN hint
        TestObserver<Token> observer = tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        
        // Assert: Token should have clientId from JWT's aud claim
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("token-client-id");
                    assertThat(token).isInstanceOf(RefreshToken.class);
                    return true;
                });
    }

    @Test
    public void when_introspect_refresh_token_should_use_original_client_id() {
        // Arrange: JWT refresh token with aud claim set to "resource-id" (RFC 8707)
        JWT jwt = new JWT();
        jwt.setJti("refresh-token-id");
        jwt.setAud("resource-id");
        jwt.setSub("user-123");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 7200);

        String originalClientId = "original-client-id";
        String callerClientId = "caller-client-id";

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.REFRESH_TOKEN), Mockito.eq(callerClientId)))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, originalClientId, JWTService.TokenType.REFRESH_TOKEN)));

        // Act: Introspect with callerClientId (different from original client) and REFRESH_TOKEN hint
        TestObserver<Token> observer = tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN, callerClientId).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        // Assert: Token should have clientId from introspection result (original client), not callerClientId
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo(originalClientId);
                    assertThat(token.getClientId()).isNotEqualTo(callerClientId);
                    assertThat(token).isInstanceOf(RefreshToken.class);
                    return true;
                });
    }

    @Test
    public void when_introspect_rfc8707_access_token_in_offline_window_should_use_client_id_claim() {
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.put(Claims.AUD, resourceAudience("https://mcp.local.test/mcp"));
        jwt.put(Claims.CLIENT_ID, "service-app");
        jwt.setSub("service-app");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, null, JWTService.TokenType.ACCESS_TOKEN)));

        TestObserver<Token> observer = tokenService.introspect("token").test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("service-app");
                    return true;
                });
    }

    @Test
    public void when_introspect_multi_resource_access_token_in_offline_window_should_use_client_id_claim() {
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.put(Claims.AUD, resourceAudience("https://mcp.local.test/mcp", "https://api.local.test/data"));
        jwt.put(Claims.CLIENT_ID, "service-app");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        Mockito.when(introspectionTokenService.introspect(Mockito.anyString(), Mockito.eq(JWTService.TokenType.ACCESS_TOKEN), Mockito.isNull()))
                .thenReturn(Maybe.just(new IntrospectionResult(jwt, null, JWTService.TokenType.ACCESS_TOKEN)));

        TestObserver<Token> observer = tokenService.introspect("token").test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("service-app");
                    return true;
                });
    }

    @Test
    public void when_get_access_token_for_rfc8707_token_should_prefer_stored_client_over_jwt_claim() {
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.put(Claims.AUD, resourceAudience("https://mcp.local.test/mcp"));
        jwt.put(Claims.CLIENT_ID, "jwt-claim-client");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        io.gravitee.am.repository.oauth2.model.AccessToken storedToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        storedToken.setClient("stored-owner");

        Client client = new Client();
        client.setClientId("stored-owner");

        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any(JWTService.TokenType.class)))
                .thenReturn(Single.just(jwt));
        when(tokenRepository.findAccessTokenByJti("access-token-id")).thenReturn(Maybe.just(storedToken));

        TestObserver<Token> observer = tokenService.getAccessToken("token", client).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("stored-owner");
                    return true;
                });
    }

    @Test
    public void when_get_access_token_and_stored_client_is_missing_should_fall_back_to_client_id_claim() {
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.put(Claims.AUD, resourceAudience("https://mcp.local.test/mcp"));
        jwt.put(Claims.CLIENT_ID, "service-app");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        io.gravitee.am.repository.oauth2.model.AccessToken storedToken = new io.gravitee.am.repository.oauth2.model.AccessToken();

        Client client = new Client();
        client.setClientId("service-app");

        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any(JWTService.TokenType.class)))
                .thenReturn(Single.just(jwt));
        when(tokenRepository.findAccessTokenByJti("access-token-id")).thenReturn(Maybe.just(storedToken));

        TestObserver<Token> observer = tokenService.getAccessToken("token", client).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("service-app");
                    return true;
                });
    }

    @Test
    public void when_get_access_token_without_client_id_claim_should_fall_back_to_aud() {
        JWT jwt = new JWT();
        jwt.setJti("access-token-id");
        jwt.setAud("legacy-client");
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);

        io.gravitee.am.repository.oauth2.model.AccessToken storedToken = new io.gravitee.am.repository.oauth2.model.AccessToken();

        Client client = new Client();
        client.setClientId("legacy-client");

        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any(JWTService.TokenType.class)))
                .thenReturn(Single.just(jwt));
        when(tokenRepository.findAccessTokenByJti("access-token-id")).thenReturn(Maybe.just(storedToken));

        TestObserver<Token> observer = tokenService.getAccessToken("token", client).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete()
                .assertNoErrors()
                .assertValue(token -> {
                    assertThat(token.getClientId()).isEqualTo("legacy-client");
                    return true;
                });
    }

    private static JSONArray resourceAudience(String... resources) {
        JSONArray audience = new JSONArray();
        audience.addAll(List.of(resources));
        return audience;
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

    // ========== Token Exchange Audit Tests ==========

    @Test
    public void when_token_exchange_should_include_exchange_params_in_audit() {
        // Arrange: Token exchange request with delegation
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("exchange-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);
        request.setSubjectTokenId("subject-jti-123");
        request.setSubjectTokenType("urn:ietf:params:oauth:token-type:access_token");
        request.setActorTokenType("urn:ietf:params:oauth:token-type:access_token");
        request.setActorTokenId("actor-jti-456");
        request.setDelegation(true);
        request.setActClaim(Map.of(Claims.SUB, "actor-sub-789"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("exchange-client");
        client.setDomain("test-domain");
        User user = createUser("user-789");
        setupCommonMocks(request);

        // Act
        executeTokenCreation(request, client, user);

        // Assert: Verify audit contains token exchange params
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());

        AuditBuilder capturedBuilder = auditCaptor.getAllValues().stream()
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ClientTokenAuditBuilder in audit reports"));

        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();

        // Token exchange specific params
        assertThat(auditMessage).contains("REQUESTED_TOKEN_TYPE");
        assertThat(auditMessage).contains(TokenType.ACCESS_TOKEN);
        assertThat(auditMessage).contains("SUBJECT_TOKEN");
        assertThat(auditMessage).contains("subject-jti-123");
        assertThat(auditMessage).contains("SUBJECT_TOKEN_TYPE");
        assertThat(auditMessage).contains("urn:ietf:params:oauth:token-type:access_token");
        assertThat(auditMessage).contains("ACTOR_TOKEN");
        assertThat(auditMessage).contains("actor-jti-456");
        assertThat(auditMessage).contains("ACTOR_TOKEN_TYPE");

        // Standard params still present
        assertThat(auditMessage).contains("GRANT_TYPE");
        assertThat(auditMessage).contains(GrantType.TOKEN_EXCHANGE);
    }

    @Test
    public void when_token_exchange_impersonation_should_not_include_actor_params_in_audit() {
        // Arrange: Token exchange without delegation (impersonation)
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("exchange-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);
        request.setSubjectTokenId("subject-jti-456");
        request.setSubjectTokenType("urn:ietf:params:oauth:token-type:access_token");
        request.setDelegation(false);
        request.setOrigin("https://auth.example.com");

        Client client = createClient("exchange-client");
        client.setDomain("test-domain");
        User user = createUser("user-456");
        setupCommonMocks(request);

        // Act
        executeTokenCreation(request, client, user);

        // Assert
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());

        AuditBuilder capturedBuilder = auditCaptor.getAllValues().stream()
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ClientTokenAuditBuilder in audit reports"));

        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();

        // Subject token params present
        assertThat(auditMessage).contains("SUBJECT_TOKEN");
        assertThat(auditMessage).contains("subject-jti-456");
        assertThat(auditMessage).contains("SUBJECT_TOKEN_TYPE");

        // Actor params NOT present (impersonation, no delegation)
        assertThat(auditMessage).doesNotContain("ACTOR_TOKEN_TYPE");
        assertThat(auditMessage).doesNotContain("ACTOR_TOKEN");
    }

    @Test
    public void when_token_exchange_id_token_only_should_include_exchange_params_in_audit() {
        // Arrange: Token exchange requesting ID token
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("exchange-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        request.setSubjectTokenId("subject-jti-789");
        request.setSubjectTokenType("urn:ietf:params:oauth:token-type:access_token");
        request.setDelegation(false);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("exchange-client");
        client.setDomain("test-domain");
        client.setIdTokenValiditySeconds(3600);
        User user = createUser("user-321");

        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(idTokenService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just("eyJhbGciOiJSUzI1NiJ9.test.signature"));

        // Act
        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        // Assert: Verify audit contains token exchange params
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());

        AuditBuilder capturedBuilder = auditCaptor.getAllValues().stream()
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ClientTokenAuditBuilder in audit reports"));

        String auditMessage = capturedBuilder.build(new ObjectMapper()).getOutcome().getMessage();

        assertThat(auditMessage).contains("GRANT_TYPE");
        assertThat(auditMessage).contains(GrantType.TOKEN_EXCHANGE);
        assertThat(auditMessage).contains("REQUESTED_TOKEN_TYPE");
        assertThat(auditMessage).contains(TokenType.ID_TOKEN);
        assertThat(auditMessage).contains("SUBJECT_TOKEN");
        assertThat(auditMessage).contains("subject-jti-789");
        assertThat(auditMessage).contains("SUBJECT_TOKEN_TYPE");
    }

    // ========== RAR authorization_details Tests ==========

    @Test
    public void emitsAuthorizationDetails_asClaimAndResponseMember_whenPresent() {
        // Arrange: request with authorization_details set (RFC 9396)
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("rar-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");
        request.setAuthorizationDetails(List.of(Map.of("type", "fdx_v1.0")));

        Client client = createClient("rar-client");
        User user = createUser("user-rar");
        setupCommonMocks(request);
        // Pass the token through the enhancer so additionalInformation is preserved
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        // Act
        TestObserver<Token> observer = executeTokenCreation(request, client, user);

        // Assert (a): JWT claim "authorization_details" is present with expected value
        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(1)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        JWT capturedJwt = jwtCaptor.getValue();
        assertThat(capturedJwt.containsKey("authorization_details")).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jwtAuthDetails = (List<Map<String, Object>>) capturedJwt.get("authorization_details");
        assertThat(jwtAuthDetails).hasSize(1);
        assertThat(jwtAuthDetails.get(0)).containsEntry("type", "fdx_v1.0");

        // Assert (b): token response additionalInformation contains "authorization_details"
        observer.assertValue(token -> {
            assertThat(token.getAdditionalInformation()).containsKey("authorization_details");
            return true;
        });
    }

    @Test
    public void omitsAuthorizationDetails_whenAbsent() {
        // Arrange: request without authorization_details
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("rar-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");
        // authorizationDetails intentionally NOT set

        Client client = createClient("rar-client");
        User user = createUser("user-rar");
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));

        // Act
        TestObserver<Token> observer = executeTokenCreation(request, client, user);

        // Assert: JWT claim "authorization_details" is absent
        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(1)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        JWT capturedJwt = jwtCaptor.getValue();
        assertThat(capturedJwt.containsKey("authorization_details")).isFalse();

        // Assert: token response additionalInformation does NOT contain "authorization_details"
        observer.assertValue(token -> {
            assertThat(token.getAdditionalInformation()).doesNotContainKey("authorization_details");
            return true;
        });
    }

    @Test
    public void shouldReturnTheGrantedResourceAndScopeInTheTokenResponse() {
        OAuth2Request request = extensionGrantRequest(true, Set.of("https://mcp.example.com/calendar"));

        Token token = createPassingThroughEnhancer(request);

        assertThat(token.getAdditionalInformation()).containsEntry("resource", "https://mcp.example.com/calendar");
        assertThat(token.getScope()).isEqualTo("calendar.read");
        assertThat(captureAccessTokenJWT().get("aud")).asList().containsExactly("https://mcp.example.com/calendar");
    }

    @Test
    public void shouldReturnTheGrantedResourceInStrictResponseMode() {
        tokenService.setStrictResponse(true);
        OAuth2Request request = extensionGrantRequest(true, Set.of("https://mcp.example.com/calendar"));

        Token token = createPassingThroughEnhancer(request);

        assertThat(token.getAdditionalInformation()).containsEntry("resource", "https://mcp.example.com/calendar");
    }

    @Test
    public void shouldNotReturnAResourceWhenNoneWasGranted() {
        OAuth2Request request = extensionGrantRequest(false, Set.of("https://mcp.example.com/calendar"));

        Token token = createPassingThroughEnhancer(request);

        assertThat(token.getAdditionalInformation()).doesNotContainKey("resource");
    }

    @Test
    public void shouldNotReturnAResourceWhenPoliciesLeaveSeveral() {
        OAuth2Request request = extensionGrantRequest(true, Set.of("https://mcp.example.com/calendar", "https://mcp.example.com/mail"));

        Token token = createPassingThroughEnhancer(request);

        assertThat(token.getAdditionalInformation()).doesNotContainKey("resource");
    }

    private OAuth2Request extensionGrantRequest(boolean resourceGranted, Set<String> resources) {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap());
        request.setClientId("xaa-client");
        request.setGrantType(GrantType.JWT_BEARER);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("calendar.read"));
        request.setOrigin("https://auth.example.com");
        request.setResources(resources);
        request.setResourceGranted(resourceGranted);
        return request;
    }

    private Token createPassingThroughEnhancer(OAuth2Request request) {
        setupCommonMocks(request);
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Single.just((Token) invocation.getArgument(0)));
        return executeTokenCreation(request, createClient(request.getClientId()), createUser("user-xaa")).values().get(0);
    }

    private JWT captureAccessTokenJWT() {
        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(1)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        return jwtCaptor.getValue();
    }


    @Test
    public void shouldRecordRequestContextWhenTokenCreationFails() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("test-client");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        request.setSupportRefreshToken(false);
        request.setScopes(Set.of("read"));
        request.setResources(Set.of("https://mcp.example.com/api"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("test-client");
        client.setDomain("test-domain");
        User user = createUser("user-1");

        when(openIDDiscoveryService.getIssuer(anyString())).thenReturn("https://auth.example.com");
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(jwtService.encodeJwt(any(JWT.class), any(Client.class))).thenReturn(Single.error(new IllegalStateException("signature failed")));

        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(IllegalStateException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage())
                .contains("signature failed",
                        "\"GRANT_TYPE\":\"" + GrantType.CLIENT_CREDENTIALS + "\"",
                        "\"SCOPE\":\"read\"",
                        "\"RESOURCE\":\"https://mcp.example.com/api\"");
        assertThat(audit.getTarget().getId()).isEqualTo("user-1");
        assertThat(audit.getAccessPoint().getAlternativeId()).isEqualTo("test-client");
    }

    @Test
    public void shouldRecordRequestContextWhenIdTokenOnlyExchangeFails() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("exchange-client");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_TOKEN);
        request.setSubjectTokenId("subject-jti-789");
        request.setScopes(Set.of("openid"));
        request.setOrigin("https://auth.example.com");

        Client client = createClient("exchange-client");
        client.setDomain("test-domain");
        User user = createUser("user-321");

        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        when(idTokenService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.error(new IllegalStateException("id token refused")));

        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(IllegalStateException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage())
                .contains("id token refused",
                        "\"GRANT_TYPE\":\"" + GrantType.TOKEN_EXCHANGE + "\"",
                        "\"REQUESTED_TOKEN_TYPE\":\"" + TokenType.ID_TOKEN + "\"",
                        "\"SUBJECT_TOKEN\":\"subject-jti-789\"",
                        "\"SCOPE\":\"openid\"");
        assertThat(audit.getTarget().getId()).isEqualTo("user-321");
    }

    private OAuth2Request idJagRequest() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("agent-at-am");
        request.setGrantType(GrantType.TOKEN_EXCHANGE);
        request.setSupportRefreshToken(false);
        request.setIssuedTokenType(TokenType.ID_JAG);
        request.setSubjectTokenId("subject-jti-1");
        request.setSubjectTokenType(TokenType.ACCESS_TOKEN);
        request.setOrigin("https://auth.example.com");
        request.setIdJagTarget(new IdJagTarget("https://auth.acme.com", "https://calendar.acme.com", "agent-at-acme", Map.of(), null));
        when(executionContextFactory.create(any())).thenReturn(new SimpleExecutionContext(request, null));
        return request;
    }

    @Test
    public void shouldHandTheIssuanceExecutionContextToTheIdJagService() {
        OAuth2Request request = idJagRequest();
        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, null)));

        tokenService.create(request, createClient("agent-at-am"), createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

        ArgumentCaptor<ExecutionContext> executionContext = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(idJagService).create(any(OAuth2Request.class), any(Client.class), any(User.class), executionContext.capture());
        assertThat(executionContext.getValue().getAttribute(ConstantKeys.USER_CONTEXT_KEY)).extracting("id").isEqualTo("user-123");
    }

    @Test
    public void shouldExposeTheIdJagTargetToTheIssuanceExecutionContext() {
        OAuth2Request request = idJagRequest();
        request.getExecutionContext().put(ConstantKeys.ID_JAG_CONTEXT_KEY, Map.of("resource", "https://forged.example.com"));
        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, null)));

        tokenService.create(request, createClient("agent-at-am"), createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

        ArgumentCaptor<ExecutionContext> executionContext = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(idJagService).create(any(OAuth2Request.class), any(Client.class), any(User.class), executionContext.capture());
        assertThat(executionContext.getValue().getAttribute(ConstantKeys.ID_JAG_CONTEXT_KEY)).isEqualTo(Map.of(
                "audience", "https://auth.acme.com",
                "resource", "https://calendar.acme.com",
                "clientId", "agent-at-acme"));
    }

    @Test
    public void shouldReturnTheAssertionAsAnIdJagResponse() {
        OAuth2Request request = idJagRequest();
        Client client = createClient("agent-at-am");
        User user = createUser("user-123");

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("eyJ0eXAiOiJvYXV0aC1pZC1qYWcrand0In0.payload.signature", "assertion-jti", 300, null)));

        TestObserver<Token> observer = tokenService.create(request, client, user).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(token -> {
            assertThat(token.getTokenType()).isEqualTo("N_A");
            assertThat(token.getIssuedTokenType()).isEqualTo(TokenType.ID_JAG);
            assertThat(token.getValue()).startsWith("eyJ");
            assertThat(token.getExpiresIn()).isEqualTo(300);
            assertThat(token.getRefreshToken()).isNull();
            return true;
        });
    }

    @Test
    public void shouldReturnTheAssertionsScopeInThePartnersVocabulary() {
        OAuth2Request request = idJagRequest();
        request.setScopes(Set.of("calendar.read"));

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, "read:calendar")));

        TestObserver<Token> observer = tokenService.create(request, createClient("agent-at-am"), createUser("user-123")).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(token -> "read:calendar".equals(token.getScope()));
    }

    @Test
    public void shouldAuditTheSecurityDomainsOwnScopeNames() {
        OAuth2Request request = idJagRequest();
        request.setScopes(Set.of("calendar.read"));
        Client client = createClient("agent-at-am");
        client.setDomain("test-domain");

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, "read:calendar")));

        tokenService.create(request, client, createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

        assertThat(captureTokenAudit().getOutcome().getMessage())
                .contains("calendar.read")
                .doesNotContain("read:calendar");
    }

    @Test
    public void shouldNotStoreOrSignAnAssertionOutsideTheIdJagService() {
        OAuth2Request request = idJagRequest();

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, null)));

        tokenService.create(request, createClient("agent-at-am"), createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

        verify(tokenManager, Mockito.never()).storeTokens(any(), any());
        verify(jwtService, Mockito.never()).encodeJwt(any(JWT.class), any(Client.class));
        verify(idTokenService, Mockito.never()).create(any(OAuth2Request.class), any(Client.class), any(User.class), any());
    }

    @Test
    public void shouldAuditIssuanceWithTheAudienceAndResolvedResource() {
        OAuth2Request request = idJagRequest();
        Client client = createClient("agent-at-am");
        client.setDomain("test-domain");

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.just(new IdJag("assertion", "assertion-jti", 300, null)));

        tokenService.create(request, client, createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(audit.getOutcome().getMessage())
                .contains(TokenTypeHint.ID_JAG.name(),
                        "assertion-jti",
                        "https://auth.acme.com",
                        "https://calendar.acme.com",
                        TokenType.ID_JAG);
        assertThat(audit.getTarget().getId()).isEqualTo("user-123");
    }

    @Test
    public void shouldAuditADeniedIssuanceWithTheSameContext() {
        OAuth2Request request = idJagRequest();
        Client client = createClient("agent-at-am");
        client.setDomain("test-domain");

        when(idJagService.create(any(OAuth2Request.class), any(Client.class), any(User.class), any()))
                .thenReturn(Single.error(new IllegalStateException("assertion refused")));

        tokenService.create(request, client, createUser("user-123")).test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(IllegalStateException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage())
                .contains("assertion refused",
                        "\"REQUESTED_TOKEN_TYPE\":\"" + TokenType.ID_JAG + "\"",
                        "\"AUDIENCE\":\"https://auth.acme.com\"",
                        "\"RESOURCE\":\"https://calendar.acme.com\"",
                        "\"SUBJECT_TOKEN\":\"subject-jti-1\"");
    }

    private Audit captureTokenAudit() {
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, Mockito.atLeastOnce()).report(auditCaptor.capture());
        return auditCaptor.getAllValues().stream()
                .filter(ClientTokenAuditBuilder.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ClientTokenAuditBuilder in audit reports"))
                .build(new ObjectMapper());
    }

    @Test
    public void lightweightJwt_keepsOnlyReservedClaimsInAccessAndRefreshTokens() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSubject("user");
        request.setPermissions(List.of(new PermissionRequest().setResourceId("rs-1")));
        request.setAuthorizationDetails(List.of(Map.of("type", "fdx_v1.0")));
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(io.gravitee.am.common.oidc.Parameters.CLAIMS, "{\"userinfo\":{\"email\":null}}");
        request.setParameters(parameters);

        Client client = lightweightClient(true);
        setupCustomClaimMocks(request);
        doAnswer(invocation -> {
            JWT jwt = invocation.getArgument(0);
            jwt.setSub("user-sub");
            jwt.setInternalSub("user-gis");
            return null;
        }).when(subjectManager).updateJWT(any(), any());

        executeTokenCreation(request, client, createUser("user"));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(2)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getAllValues().get(0)).containsOnlyKeys(
                Claims.ISS, Claims.SUB, Claims.GIO_INTERNAL_SUB, Claims.AUD, Claims.DOMAIN, Claims.IAT, Claims.EXP, Claims.JTI,
                Claims.SCOPE, Claims.CLIENT_ID, Claims.CLAIMS, "authorization_details", "permissions");
        assertThat(jwtCaptor.getAllValues().get(1)).containsOnlyKeys(
                Claims.ISS, Claims.SUB, Claims.GIO_INTERNAL_SUB, Claims.AUD, Claims.DOMAIN, Claims.IAT, Claims.EXP, Claims.JTI,
                Claims.SCOPE, "authorization_details", "permissions");
    }

    @Test
    public void lightweightJwt_keepsOrigResourcesAndDpopBindingInRefreshToken() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSubject("user");
        request.setResources(Set.of("https://api.example.com"));
        request.setConfirmationMethodJkt("the-jkt");

        Client client = lightweightClient(true);
        setupCustomClaimMocks(request);

        executeTokenCreation(request, client, createUser("user"));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(2)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getAllValues().get(1)).containsKeys(Claims.ORIG_RESOURCES, Claims.CNF).doesNotContainKey("tenant");
    }

    @Test
    public void lightweightJwt_keepsConditionalReservedClaimsInAccessToken() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSupportRefreshToken(false);
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        request.setConfirmationMethodX5S256("the-cert-thumbprint");
        request.setResources(Set.of("https://api.example.com"));

        Client client = lightweightClient(true);
        client.setAppType(ApplicationType.AGENT);
        client.setAgentType(AgentType.AUTONOMOUS);
        setupCustomClaimMocks(request);

        executeTokenCreation(request, client, null);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encodeJwt(jwtCaptor.capture(), any(Client.class));
        JWT accessToken = jwtCaptor.getValue();
        assertThat(accessToken).containsKeys(Claims.CNF, Claims.ACT, Claims.CLIENT_PROFILE)
                .doesNotContainKeys("tenant", Claims.SUB_PROFILE);
        assertThat((List<String>) accessToken.get(Claims.AUD)).containsExactly("https://api.example.com");
    }

    @Test
    public void lightweightJwt_keepsCustomClaimsNamedAfterKeptClaims() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSubject("user");
        request.setSupportRefreshToken(false);

        Client client = lightweightClient(true);
        client.setTokenCustomClaims(List.of(
                TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, Claims.SUB, "custom-sub"),
                TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, Claims.SCOPE, "custom-scope")));
        setupCustomClaimMocks(request);

        executeTokenCreation(request, client, createUser("user"));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getValue()).containsEntry(Claims.SUB, "custom-sub")
                .containsEntry(Claims.SCOPE, "custom-scope");
    }

    @Test
    public void lightweightJwt_keepsUmaPermissionsInAccessToken() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSubject("user");
        request.setSupportRefreshToken(false);
        request.setGrantType(GrantType.UMA);
        List<PermissionRequest> permissions = List.of(new PermissionRequest().setResourceId("rs-1").setResourceScopes(List.of("read")));
        request.setPermissions(permissions);

        Client client = lightweightClient(true);
        setupCustomClaimMocks(request);

        executeTokenCreation(request, client, createUser("user"));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getValue()).containsEntry("permissions", permissions).doesNotContainKey("tenant");
    }

    @Test
    public void lightweightJwtDisabled_keepsCustomClaimsInAccessAndRefreshTokens() {
        OAuth2Request request = authorizationCodeRequest();
        request.setSubject("user");
        request.setPermissions(List.of(new PermissionRequest().setResourceId("rs-1")));

        Client client = lightweightClient(false);
        setupCustomClaimMocks(request);

        executeTokenCreation(request, client, createUser("user"));

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService, Mockito.times(2)).encodeJwt(jwtCaptor.capture(), any(Client.class));
        assertThat(jwtCaptor.getAllValues().get(0)).containsEntry("tenant", "acme").containsKey("permissions");
        assertThat(jwtCaptor.getAllValues().get(1)).containsEntry("tenant", "acme");
    }

    private OAuth2Request authorizationCodeRequest() {
        OAuth2Request request = new OAuth2Request();
        request.setParameters(new LinkedMultiValueMap<>());
        request.setClientId("lightweight-client");
        request.setGrantType(GrantType.AUTHORIZATION_CODE);
        request.setSupportRefreshToken(true);
        request.setScopes(Set.of("read"));
        request.setOrigin("https://auth.example.com");
        return request;
    }

    private void setupCustomClaimMocks(OAuth2Request request) {
        when(openIDDiscoveryService.getIssuer(anyString())).thenReturn("https://auth.example.com");
        when(jwtService.encodeJwt(any(JWT.class), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("access-token")));
        when(tokenManager.storeTokens(any(), any())).thenReturn(Completable.complete());
        TemplateEngine templateEngine = Mockito.mock(TemplateEngine.class);
        when(templateEngine.getValue(anyString(), eq(Object.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutionContext executionContext = spy(new SimpleExecutionContext(request, null));
        doReturn(templateEngine).when(executionContext).getTemplateEngine();
        when(executionContextFactory.create(any())).thenReturn(executionContext);
    }

    private Client lightweightClient(boolean lightweightJwtEnabled) {
        Client client = createClient("lightweight-client");
        client.setDomain("test-domain");
        client.setTokenCustomClaims(List.of(TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, "tenant", "acme")));
        client.setLightweightJwtSettings(ApplicationLightweightJwtSettings.builder().enabled(lightweightJwtEnabled).build());
        return client;
    }
}
