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
package io.gravitee.am.gateway.handler.oauth2.service.grant;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
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

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyGranterAdapterTest {

    @Mock
    private GrantStrategy strategy;

    @Mock
    private TokenService tokenService;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private TokenRequestResolver tokenRequestResolver;

    private StrategyGranterAdapter adapter;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));

        lenient().when(tokenRequestResolver.resolve(any(), any(), any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        adapter = new StrategyGranterAdapter(strategy, domain, tokenService, rulesEngine, tokenRequestResolver);
    }

    @Test
    void shouldDelegateHandleToStrategy() {
        when(strategy.supports(eq(GrantType.CLIENT_CREDENTIALS), eq(client), eq(domain)))
                .thenReturn(true);

        assertTrue(adapter.handle(GrantType.CLIENT_CREDENTIALS, client));
    }

    @Test
    void shouldReturnFalseWhenStrategyDoesNotSupport() {
        when(strategy.supports(eq(GrantType.PASSWORD), eq(client), eq(domain)))
                .thenReturn(false);

        assertFalse(adapter.handle(GrantType.PASSWORD, client));
    }

    @Test
    void shouldProcessGrantSuccessfully() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read"));

        TokenCreationRequest creationRequest = TokenCreationRequest.forClientCredentials(tokenRequest, false);

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        Map<String, Object> attributes = new HashMap<>();
        when(executionContext.getAttributes()).thenReturn(attributes);

        when(rulesEngine.fire(any(), any(), any(), eq(client), any()))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(any(), any(), eq(client), any()))
                .thenReturn(Single.just(executionContext));

        when(tokenService.create(any(), eq(client), any()))
                .thenReturn(Single.just(expectedToken));

        Token result = adapter.grant(tokenRequest, client).blockingGet();

        assertNotNull(result);
        assertEquals("access-token-value", result.getValue());
    }

    @Test
    void shouldConvertTokenCreationRequestToOAuth2Request() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read", "write"));

        User user = new User();
        user.setId("user-id");

        TokenCreationRequest creationRequest = new TokenCreationRequest(
                "client-id",
                GrantType.PASSWORD,
                Set.of("read", "write"),
                user,
                new GrantData.PasswordData("testuser"),
                true,
                Set.of("https://api.example.com"),
                Set.of("https://api.example.com"),
                HttpRequestInfo.from(tokenRequest),
                tokenRequest.getAdditionalParameters(),
                tokenRequest.getContext(),
                Map.of()
        );

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(any(), any(), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(any(), any(), eq(client), any()))
                .thenReturn(Single.just(executionContext));

        when(tokenService.create(any(), eq(client), eq(user)))
                .thenReturn(Single.just(expectedToken));

        Token result = adapter.grant(tokenRequest, client).blockingGet();

        assertNotNull(result);
    }

    @Test
    void shouldGetUnderlyingStrategy() {
        assertSame(strategy, adapter.getStrategy());
    }

    @Test
    void shouldPreservePreTokenPolicyModifications() {
        // This test verifies that modifications made by PRE_TOKEN policy
        // to the OAuth2Request are preserved and passed to token creation.
        // This was a regression in the new strategy-based architecture.

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read"));

        TokenCreationRequest creationRequest = TokenCreationRequest.forClientCredentials(tokenRequest, false);

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        // Capture the OAuth2Request passed to PRE_TOKEN and modify it (simulating policy behavior)
        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), any(OAuth2Request.class), any(), eq(client), any()))
                .thenAnswer(invocation -> {
                    OAuth2Request oAuth2Request = invocation.getArgument(1);
                    // PRE_TOKEN policy modifies the request
                    oAuth2Request.setScopes(Set.of("read", "write", "admin"));
                    oAuth2Request.getExecutionContext().put("policy_added", "value_from_policy");

                    // Return ExecutionContext with attributes that will be merged
                    ExecutionContext preTokenContext = mock(ExecutionContext.class);
                    Map<String, Object> preTokenAttributes = new HashMap<>();
                    preTokenAttributes.put("custom_claim", "custom_value");
                    when(preTokenContext.getAttributes()).thenReturn(preTokenAttributes);
                    return Single.just(preTokenContext);
                });

        ExecutionContext postTokenContext = mock(ExecutionContext.class);
        lenient().when(postTokenContext.getAttributes()).thenReturn(new HashMap<>());
        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), any(), eq(client), any()))
                .thenReturn(Single.just(postTokenContext));

        // Capture the OAuth2Request passed to tokenService.create()
        ArgumentCaptor<OAuth2Request> oAuth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestCaptor.capture(), eq(client), any()))
                .thenReturn(Single.just(expectedToken));

        Token result = adapter.grant(tokenRequest, client).blockingGet();

        assertNotNull(result);

        // Verify that the OAuth2Request passed to tokenService.create() has the modifications
        // made by PRE_TOKEN policy
        OAuth2Request capturedRequest = oAuth2RequestCaptor.getValue();
        assertEquals(Set.of("read", "write", "admin"), capturedRequest.getScopes(),
                "PRE_TOKEN policy scope modifications should be preserved");
        assertEquals("value_from_policy", capturedRequest.getExecutionContext().get("policy_added"),
                "PRE_TOKEN policy execution context modifications should be preserved");
    }

    @Test
    void shouldUseSameOAuth2RequestThroughoutFlow() {
        // Verify that the same OAuth2Request instance is used for PRE_TOKEN, token creation, and POST_TOKEN

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("read"));

        TokenCreationRequest creationRequest = TokenCreationRequest.forClientCredentials(tokenRequest, false);

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        // Capture OAuth2Request from each step
        ArgumentCaptor<OAuth2Request> preTokenCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        ArgumentCaptor<OAuth2Request> createTokenCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        ArgumentCaptor<OAuth2Request> postTokenCaptor = ArgumentCaptor.forClass(OAuth2Request.class);

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), preTokenCaptor.capture(), any(), eq(client), any()))
                .thenReturn(Single.just(executionContext));

        when(tokenService.create(createTokenCaptor.capture(), eq(client), any()))
                .thenReturn(Single.just(expectedToken));

        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), postTokenCaptor.capture(), eq(client), any()))
                .thenReturn(Single.just(executionContext));

        adapter.grant(tokenRequest, client).blockingGet();

        // Verify all three steps received the same OAuth2Request instance
        OAuth2Request preTokenRequest = preTokenCaptor.getValue();
        OAuth2Request createTokenRequest = createTokenCaptor.getValue();
        OAuth2Request postTokenRequest = postTokenCaptor.getValue();

        assertSame(preTokenRequest, createTokenRequest,
                "PRE_TOKEN and token creation should use the same OAuth2Request instance");
        assertSame(createTokenRequest, postTokenRequest,
                "Token creation and POST_TOKEN should use the same OAuth2Request instance");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeGisInActClaimWhenActorHasGis() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("openid"));

        User user = new User();
        user.setId("subject-user");

        // Create TokenExchangeData with actorInfo that has gis
        ActorTokenInfo actorInfo = new ActorTokenInfo("actor-sub", "source:actor-id", null, null, 1);
        GrantData.TokenExchangeData exchangeData = new GrantData.TokenExchangeData(
                "urn:ietf:params:oauth:token-type:access_token",
                new Date(),
                "subject-token-id",
                "urn:ietf:params:oauth:token-type:access_token",
                actorInfo
        );

        TokenCreationRequest creationRequest = new TokenCreationRequest(
                "client-id",
                GrantType.TOKEN_EXCHANGE,
                Set.of("openid"),
                user,
                exchangeData,
                false,
                Set.of(),
                Set.of(),
                HttpRequestInfo.from(tokenRequest),
                tokenRequest.getAdditionalParameters(),
                tokenRequest.getContext(),
                Map.of()
        );

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), any(OAuth2Request.class), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));

        ArgumentCaptor<OAuth2Request> oAuth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestCaptor.capture(), eq(client), eq(user)))
                .thenReturn(Single.just(expectedToken));

        adapter.grant(tokenRequest, client).blockingGet();

        OAuth2Request capturedRequest = oAuth2RequestCaptor.getValue();
        assertTrue(capturedRequest.isDelegation(), "Should be delegation");
        assertNotNull(capturedRequest.getActClaim(), "Should have act claim");

        Map<String, Object> actClaim = capturedRequest.getActClaim();
        assertEquals("actor-sub", actClaim.get(Claims.SUB), "Act claim should have actor sub");
        assertEquals("source:actor-id", actClaim.get(Claims.GIO_INTERNAL_SUB), "Act claim should have actor gis");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotIncludeGisInActClaimWhenActorHasNoGis() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("openid"));

        User user = new User();
        user.setId("subject-user");

        // Create TokenExchangeData with actorInfo that has no gis
        ActorTokenInfo actorInfo = new ActorTokenInfo("actor-sub", null, null, null, 1);
        GrantData.TokenExchangeData exchangeData = new GrantData.TokenExchangeData(
                "urn:ietf:params:oauth:token-type:access_token",
                new Date(),
                "subject-token-id",
                "urn:ietf:params:oauth:token-type:access_token",
                actorInfo
        );

        TokenCreationRequest creationRequest = new TokenCreationRequest(
                "client-id",
                GrantType.TOKEN_EXCHANGE,
                Set.of("openid"),
                user,
                exchangeData,
                false,
                Set.of(),
                Set.of(),
                HttpRequestInfo.from(tokenRequest),
                tokenRequest.getAdditionalParameters(),
                tokenRequest.getContext(),
                Map.of()
        );

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), any(OAuth2Request.class), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));

        ArgumentCaptor<OAuth2Request> oAuth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestCaptor.capture(), eq(client), eq(user)))
                .thenReturn(Single.just(expectedToken));

        adapter.grant(tokenRequest, client).blockingGet();

        OAuth2Request capturedRequest = oAuth2RequestCaptor.getValue();
        assertTrue(capturedRequest.isDelegation(), "Should be delegation");
        assertNotNull(capturedRequest.getActClaim(), "Should have act claim");

        Map<String, Object> actClaim = capturedRequest.getActClaim();
        assertEquals("actor-sub", actClaim.get(Claims.SUB), "Act claim should have actor sub");
        assertFalse(actClaim.containsKey(Claims.GIO_INTERNAL_SUB), "Act claim should NOT have gis when actor has no gis");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeActorActClaimWhenActorTokenIsDelegated() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("openid"));

        User user = new User();
        user.setId("subject-user");

        // Create a nested act claim representing the actor token's own delegation chain
        Map<String, Object> actorTokenActClaim = Map.of(
                Claims.SUB, "original-actor-sub",
                Claims.GIO_INTERNAL_SUB, "source:original-actor-id"
        );

        // Create TokenExchangeData with actorInfo that has actor_act (actor token is delegated)
        ActorTokenInfo actorInfo = new ActorTokenInfo("actor-sub", "source:actor-id", null, actorTokenActClaim, 1);
        GrantData.TokenExchangeData exchangeData = new GrantData.TokenExchangeData(
                "urn:ietf:params:oauth:token-type:access_token",
                new Date(),
                "subject-token-id",
                "urn:ietf:params:oauth:token-type:access_token",
                actorInfo
        );

        TokenCreationRequest creationRequest = new TokenCreationRequest(
                "client-id",
                GrantType.TOKEN_EXCHANGE,
                Set.of("openid"),
                user,
                exchangeData,
                false,
                Set.of(),
                Set.of(),
                HttpRequestInfo.from(tokenRequest),
                tokenRequest.getAdditionalParameters(),
                tokenRequest.getContext(),
                Map.of()
        );

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), any(OAuth2Request.class), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));

        ArgumentCaptor<OAuth2Request> oAuth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestCaptor.capture(), eq(client), eq(user)))
                .thenReturn(Single.just(expectedToken));

        adapter.grant(tokenRequest, client).blockingGet();

        OAuth2Request capturedRequest = oAuth2RequestCaptor.getValue();
        assertTrue(capturedRequest.isDelegation(), "Should be delegation");
        assertNotNull(capturedRequest.getActClaim(), "Should have act claim");

        Map<String, Object> actClaim = capturedRequest.getActClaim();
        assertEquals("actor-sub", actClaim.get(Claims.SUB), "Act claim should have actor sub");
        assertEquals("source:actor-id", actClaim.get(Claims.GIO_INTERNAL_SUB), "Act claim should have actor gis");

        // Verify actor_act is included
        assertTrue(actClaim.containsKey("actor_act"), "Act claim should have actor_act when actor token is delegated");
        Map<String, Object> actorAct = (Map<String, Object>) actClaim.get("actor_act");
        assertEquals("original-actor-sub", actorAct.get(Claims.SUB), "actor_act should have original actor sub");
        assertEquals("source:original-actor-id", actorAct.get(Claims.GIO_INTERNAL_SUB), "actor_act should have original actor gis");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotIncludeActorActClaimWhenActorTokenIsNotDelegated() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setScopes(Set.of("openid"));

        User user = new User();
        user.setId("subject-user");

        // Create TokenExchangeData with actorInfo that has NO actor_act (actor token is not delegated)
        ActorTokenInfo actorInfo = new ActorTokenInfo("actor-sub", "source:actor-id", null, null, 1);
        GrantData.TokenExchangeData exchangeData = new GrantData.TokenExchangeData(
                "urn:ietf:params:oauth:token-type:access_token",
                new Date(),
                "subject-token-id",
                "urn:ietf:params:oauth:token-type:access_token",
                actorInfo
        );

        TokenCreationRequest creationRequest = new TokenCreationRequest(
                "client-id",
                GrantType.TOKEN_EXCHANGE,
                Set.of("openid"),
                user,
                exchangeData,
                false,
                Set.of(),
                Set.of(),
                HttpRequestInfo.from(tokenRequest),
                tokenRequest.getAdditionalParameters(),
                tokenRequest.getContext(),
                Map.of()
        );

        Token expectedToken = new AccessToken("access-token-value");

        when(strategy.process(eq(tokenRequest), eq(client), eq(domain)))
                .thenReturn(Single.just(creationRequest));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());

        when(rulesEngine.fire(eq(ExtensionPoint.PRE_TOKEN), any(OAuth2Request.class), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(eq(ExtensionPoint.POST_TOKEN), any(), eq(client), eq(user)))
                .thenReturn(Single.just(executionContext));

        ArgumentCaptor<OAuth2Request> oAuth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestCaptor.capture(), eq(client), eq(user)))
                .thenReturn(Single.just(expectedToken));

        adapter.grant(tokenRequest, client).blockingGet();

        OAuth2Request capturedRequest = oAuth2RequestCaptor.getValue();
        assertTrue(capturedRequest.isDelegation(), "Should be delegation");
        assertNotNull(capturedRequest.getActClaim(), "Should have act claim");

        Map<String, Object> actClaim = capturedRequest.getActClaim();
        assertEquals("actor-sub", actClaim.get(Claims.SUB), "Act claim should have actor sub");
        assertFalse(actClaim.containsKey("actor_act"), "Act claim should NOT have actor_act when actor token is not delegated");
    }
}
