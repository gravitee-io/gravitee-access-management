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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.ExtensionGrantPluginType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext.BindingMode;
import io.gravitee.am.gateway.handler.oauth2.service.grant.StrategyGranterAdapter;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertionCarrying;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.plainJwtAssertion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossAppAccessGrantStrategyTest {

    private static final String ORIGIN = "https://gateway.example.com";
    private static final String DOMAIN_ISSUER = "https://gateway.example.com/domain-b/oidc";
    private static final String ENTERPRISE_ISSUER = "https://gateway.example.com/domain-a/oidc";
    private static final String IDENTITY_PROVIDER = "idp-id";
    private static final String MCP_SERVER = "https://mcp.example.com/calendar";
    private static final String OTHER_MCP_SERVER = "https://mcp.example.com/mail";

    @Mock
    private ExtensionGrantProvider extensionGrantProvider;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserGatewayService userService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private ScopeManager scopeManager;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private TokenService tokenService;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private AuditService auditService;

    private final Logger oauth2Logger = (Logger) LoggerFactory.getLogger("io.gravitee.am.gateway.handler.oauth2");

    private ListAppender<ILoggingEvent> capturedLogs;

    private CrossAppAccessGrantStrategy strategy;
    private ExtensionGrant extensionGrant;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        extensionGrant = new ExtensionGrant();
        extensionGrant.setId("caa-id");
        extensionGrant.setType(ExtensionGrantPluginType.CROSS_APP_ACCESS);
        extensionGrant.setGrantType(GrantType.JWT_BEARER);
        extensionGrant.setCreatedAt(new Date(2000));

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~caa-id"));
        client.setScopeSettings(List.of(new ApplicationScopeSettings("calendar.read"), new ApplicationScopeSettings("calendar.write")));

        strategy = new CrossAppAccessGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                null,
                domain,
                openIDDiscoveryService,
                protectedResourceManager,
                scopeManager);
    }

    @AfterEach
    void stopCapturingLogs() {
        if (capturedLogs != null) {
            oauth2Logger.detachAppender(capturedLogs);
            oauth2Logger.setLevel(null);
        }
    }

    @Test
    void shouldSupportIdJagRequestWhenClientAuthorizesSuffixedGrantTypeOfNewerGrant() {
        pluginAcceptsAssertion();
        strategy.setOldestExtensionGrantId("older-caa-id");
        assertTrue(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldSupportIdJagRequestWhenClientAuthorizesBareGrantTypeAndGrantIsOldestCrossAppAccessGrant() {
        pluginAcceptsAssertion();
        strategy.setOldestExtensionGrantId("caa-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER));
        assertTrue(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportBareGrantTypeWhenAnotherCrossAppAccessGrantIsOldest() {
        strategy.setOldestExtensionGrantId("older-caa-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportAssertionThePluginDeclines() {
        when(extensionGrantProvider.supports(any())).thenReturn(false);

        assertFalse(strategy.supports(jwtBearerRequest(plainJwtAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypesEvenWithIdJagAssertion() {
        TokenRequest request = new TokenRequest();
        request.setClientId("client-id");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.ASSERTION, idJagAssertion());
        request.setParameters(parameters);
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER, GrantType.CLIENT_CREDENTIALS));

        assertFalse(strategy.supports(request, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotAuthorizeJwtBearer() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportWhenClientAuthorizesAnotherJwtBearerGrantOnly() {
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~other-id"));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportGrantTypeAlone() {
        strategy.setOldestExtensionGrantId("caa-id");
        assertFalse(strategy.supports(GrantType.JWT_BEARER, client, domain));
    }

    private void pluginAcceptsAssertion() {
        when(extensionGrantProvider.supports(any())).thenReturn(true);
    }

    @Test
    void shouldRedeemVerifiedAssertionForTheAssertionSubjectInTransientMode() {
        String assertion = idJagAssertion();
        givenRedeemableAssertion(verifiedClaims());

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(assertion), client, domain).blockingGet();

        assertEquals("alice", creationRequest.resourceOwner().getId());
        assertEquals("client-id", creationRequest.clientId());
        verify(extensionGrantProvider).resolveEndUser(argThat(pluginRequest ->
                assertion.equals(pluginRequest.getRequestParameters().get(Parameters.ASSERTION))
                        && "client-id".equals(pluginRequest.getClientId())));
        verify(extensionGrantProvider, never()).grant(any());
        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldAcceptAssertionIssuedByAnotherDomainOnTheSameGateway() {
        givenRedeemableAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldRefuseAssertionIssuedByThisDomainForTheRequestOrigin() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.ISS, DOMAIN_ISSUER);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion was issued by this domain"));

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldRefuseAssertionWhoseAudienceArrayOmitsTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, List.of("https://other-as.example.com", ENTERPRISE_ISSUER));
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldRefuseAssertionWhoseAudienceStringIsAnotherIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, "https://other-as.example.com");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldRefuseAssertionWithoutAudience() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.AUD);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldAcceptAudienceStringEqualToTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, DOMAIN_ISSUER);
        givenRedeemableAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldAcceptAudienceArrayContainingTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, List.of("https://other-as.example.com", DOMAIN_ISSUER));
        givenRedeemableAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldRefuseAssertionMintedForAnotherClient() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldRefuseAssertionWithoutClientId() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.CLIENT_ID);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));
    }

    @Test
    void shouldRefuseAssertionWithNonStringClientId() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, 42L);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));
    }

    @Test
    void shouldNotLogTheRawAssertion() {
        String assertion = idJagAssertion();
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);
        ListAppender<ILoggingEvent> logs = captureLogs();

        strategy.process(redemptionRequest(assertion), client, domain).test().assertError(InvalidGrantException.class);

        assertFalse(logs.list.isEmpty());
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(assertion)));
    }

    @Test
    void shouldCompareAssertionClientIdWithTheResolvedPublicClient() {
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.NONE);
        givenRedeemableAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldReportTheFirstFailingCheckInRedemptionOrder() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, "https://other-as.example.com");
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldReportSelfIssuedAssertionBeforeAudienceAndClientChecks() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.ISS, DOMAIN_ISSUER);
        claims.put(Claims.AUD, "https://other-as.example.com");
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion was issued by this domain"));
    }

    @Test
    void shouldBindLocalUserByExternalIdSubjectAndResolvedIdentityProviderInCheckUserMode() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        localUser.setExternalId("alice");
        localUser.setSource(IDENTITY_PROVIDER);
        givenRedeemableAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verifyNoInteractions(identityProviderManager, userAuthenticationManager);
    }

    @Test
    void shouldBindWithTheVerifiedSubjectClaim() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar.read"));
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        DefaultUser projectedEndUser = new DefaultUser("mallory");
        projectedEndUser.setId("mallory");
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(projectedEndUser, IDENTITY_PROVIDER, verifiedClaims(), List.of())));
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
    }

    @Test
    void shouldRefuseWhenNoLocalUserMatchesTheAssertionSubjectInCheckUserMode() {
        extensionGrant.setUserExists(true);
        givenRedeemableAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.empty());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("No user matches the assertion subject"));

        verifyNoInteractions(identityProviderManager);
    }

    @Test
    void shouldRefuseWithoutLookingUpUsersWhenVerifiedSubjectIsMissingInCheckUserMode() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.SUB);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar.read"));
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(new DefaultUser("alice"), IDENTITY_PROVIDER, claims, List.of())));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("No user matches the assertion subject"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldBindTheOnlyUserMatchingABindingRuleOnTheVerifiedEmail() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));
        User localUser = localUser("local-user-id");
        givenUsersMatchingTheBindingFilter(localUser);
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        assertEquals(attributeEquals("emails.value", "alice@example.com").toString(), bindingFilter().toString());
        verify(userService, never()).findByExternalIdAndSource(any(), any());
        verifyNoInteractions(identityProviderManager, userAuthenticationManager);
    }

    @Test
    void shouldBindTheOnlyUserMatchingABindingRuleOnTheVerifiedAudSub() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put("aud_sub", "alice-at-domain-b");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("userName", "{#token['aud_sub']}")));
        User localUser = localUser("local-user-id");
        givenUsersMatchingTheBindingFilter(localUser);
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        assertEquals(attributeEquals("userName", "alice-at-domain-b").toString(), bindingFilter().toString());
    }

    @Test
    void shouldRequireEveryBindingRuleToMatch() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(
                bindingCriterion("emails.value", "{#token['email']}"),
                bindingCriterion("userName", "{#token['sub']}")));
        User localUser = localUser("local-user-id");
        givenUsersMatchingTheBindingFilter(localUser);
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        FilterCriteria allRules = new FilterCriteria("and", null, null, false, List.of(
                attributeEquals("emails.value", "alice@example.com"),
                attributeEquals("userName", "alice")));
        assertEquals(allRules.toString(), bindingFilter().toString());
    }

    @Test
    void shouldEvaluateBindingRulesAgainstTheVerifiedClaimsOnly() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));
        User localUser = localUser("local-user-id");
        givenUsersMatchingTheBindingFilter(localUser);
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        strategy.process(redemptionRequest(idJagAssertionCarrying("{\"email\":\"mallory@example.com\"}")), client, domain).blockingGet();

        assertEquals(attributeEquals("emails.value", "alice@example.com").toString(), bindingFilter().toString());
    }

    @Test
    void shouldRefuseWhenBindingRulesMatchNoUser() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));
        givenUsersMatchingTheBindingFilter();

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("No user matches the binding rules"));

        verify(userService, never()).findByExternalIdAndSource(any(), any());
    }

    @Test
    void shouldRefuseWhenBindingRulesMatchSeveralUsers() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));
        givenUsersMatchingTheBindingFilter(localUser("alice-1"), localUser("alice-2"));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Several users match the binding rules"));

        verify(userService, never()).enhance(any());
    }

    @Test
    void shouldRefuseMalformedBindingExpressionWithoutLookingUpUsers() {
        extensionGrant.setUserExists(true);
        givenRedeemableAssertion(verifiedClaims(), List.of(bindingCriterion("emails.value", "{#token['email'}")));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Binding rules cannot be evaluated against the assertion"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldRefuseBindingExpressionThatFailsToEvaluateWithoutLookingUpUsers() {
        extensionGrant.setUserExists(true);
        givenRedeemableAssertion(verifiedClaims(), List.of(bindingCriterion("emails.value", "{#token['email'].toLowerCase()}")));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Binding rules cannot be evaluated against the assertion"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldRefuseBindingExpressionEvaluatingToNothingWithoutLookingUpUsers() {
        extensionGrant.setUserExists(true);
        givenRedeemableAssertion(verifiedClaims(), List.of(
                bindingCriterion("userName", "{#token['sub']}"),
                bindingCriterion("emails.value", "{#token['email']}")));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Binding rules cannot be evaluated against the assertion"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldRefuseBindingExpressionEvaluatingToBlankWithoutLookingUpUsers() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "  ");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Binding rules cannot be evaluated against the assertion"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldRefuseBindingRulesWithoutAnyUsableRuleWithoutLookingUpUsers() {
        extensionGrant.setUserExists(true);
        givenRedeemableAssertion(verifiedClaims(), List.of(bindingCriterion(" ", "{#token['sub']}"), bindingCriterion("userName", "")));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Binding rules cannot be evaluated against the assertion"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldBindByExternalIdSubjectAndResolvedIdentityProviderWhenBindingCriteriaAreAbsent() {
        extensionGrant.setUserExists(true);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar.read"));
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(new DefaultUser("alice"), IDENTITY_PROVIDER, verifiedClaims(), null)));
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser("local-user-id")));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verify(userService, never()).findByCriteria(any(FilterCriteria.class));
    }

    @Test
    void shouldIgnoreBindingRulesInCreateUserMode() {
        extensionGrant.setCreateUser(true);
        givenRedeemableAssertion(verifiedClaims(), List.of(bindingCriterion("emails.value", "{#token['email']}")));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(localUser("local-user-id")));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verifyNoInteractions(userService);
    }

    @Test
    void shouldIgnoreBindingRulesInTransientMode() {
        givenRedeemableAssertion(verifiedClaims(), List.of(bindingCriterion("emails.value", "{#token['email']}")));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("alice", creationRequest.resourceOwner().getId());
        verifyNoInteractions(userService);
    }

    @Test
    void shouldPersistOnlyTheProjectedProfileFromTheResolvedIdentityProviderInCreateUserMode() {
        extensionGrant.setCreateUser(true);
        User connectedUser = new User();
        connectedUser.setId("local-user-id");
        givenRedeemableAssertion(verifiedClaims());
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verify(userAuthenticationManager).connect(argThat(persisted -> "alice".equals(persisted.getId())
                && IDENTITY_PROVIDER.equals(persisted.getAdditionalInformation().get("source"))
                && !persisted.getAdditionalInformation().containsKey(Claims.JTI)
                && !persisted.getAdditionalInformation().containsKey(Claims.AUD)
                && !persisted.getAdditionalInformation().containsKey(Claims.SCOPE)), any(), eq(false));
        verifyNoInteractions(userService, identityProviderManager);
    }

    @Test
    void shouldCarryTheAssertionSubjectWithoutAssertionClaimsInTransientMode() {
        givenRedeemableAssertion(verifiedClaims());

        User resourceOwner = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet().resourceOwner();

        assertEquals("alice", resourceOwner.getId());
        assertEquals(Map.of(Claims.SUB, "alice"), resourceOwner.getAdditionalInformation());
    }

    @Test
    void shouldNotSupportRefreshTokenEvenWhenApplicationAllowsRefreshTokenGrant() {
        extensionGrant.setUserExists(true);
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~caa-id", GrantType.REFRESH_TOKEN));
        User localUser = new User();
        localUser.setId("local-user-id");
        givenRedeemableAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertFalse(creationRequest.supportRefreshToken());
    }

    @Test
    void shouldProjectVerifiedAssertionContextIntoExtensionGrantDataOnSuccess() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenRedeemableAssertion(verifiedClaims());

        TokenCreationRequest creationRequest = strategy.process(request, client, domain).blockingGet();

        IdJagAssertionContext expected = new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "client-id",
                MCP_SERVER, Set.of("calendar.read"), BindingMode.TRANSIENT, "alice");
        assertEquals(expected, request.getIdJagAssertionContext());
        assertEquals(expected, ((GrantData.ExtensionGrantData) creationRequest.grantData()).idJagAssertionContext());
    }

    @Test
    void shouldRecordSubjectBindingAndTheBoundUserOnTheAssertionContext() {
        extensionGrant.setUserExists(true);
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenRedeemableAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser("local-user-id")));

        strategy.process(request, client, domain).blockingGet();

        assertEquals(BindingMode.SUBJECT, request.getIdJagAssertionContext().bindingMode());
        assertEquals("local-user-id", request.getIdJagAssertionContext().boundUser());
    }

    @Test
    void shouldRecordRuleBindingAndTheBoundUserOnTheAssertionContext() {
        extensionGrant.setUserExists(true);
        TokenRequest request = redemptionRequest(idJagAssertion());
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaims.EMAIL, "alice@example.com");
        givenRedeemableAssertion(claims, List.of(bindingCriterion("emails.value", "{#token['email']}")));
        User localUser = localUser("local-user-id");
        givenUsersMatchingTheBindingFilter(localUser);
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        strategy.process(request, client, domain).blockingGet();

        assertEquals(BindingMode.BINDING_RULES, request.getIdJagAssertionContext().bindingMode());
        assertEquals("local-user-id", request.getIdJagAssertionContext().boundUser());
    }

    @Test
    void shouldRecordCreateUserBindingAndTheConnectedUserOnTheAssertionContext() {
        extensionGrant.setCreateUser(true);
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenRedeemableAssertion(verifiedClaims());
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(localUser("local-user-id")));

        strategy.process(request, client, domain).blockingGet();

        assertEquals(BindingMode.CREATE_USER, request.getIdJagAssertionContext().bindingMode());
        assertEquals("local-user-id", request.getIdJagAssertionContext().boundUser());
    }

    @Test
    void shouldRecordTheBindingModeWithoutABoundUserWhenBindingRefuses() {
        extensionGrant.setUserExists(true);
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenRedeemableAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.empty());

        strategy.process(request, client, domain).test().assertError(refusal("No user matches the assertion subject"));

        assertEquals(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "client-id",
                MCP_SERVER, Set.of("calendar.read"), BindingMode.SUBJECT, null), request.getIdJagAssertionContext());
    }

    @Test
    void shouldHandTheAssertionContextToTokenCreation() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenRedeemableAssertion(verifiedClaims());

        OAuth2Request accessTokenRequest = accessTokenRequestIssuedFor(request);

        assertEquals(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "client-id",
                MCP_SERVER, Set.of("calendar.read"), BindingMode.TRANSIENT, "alice"), accessTokenRequest.getIdJagAssertionContext());
    }

    @Test
    void shouldAuditARefusalWithTheVerifiedFactsAndNeverTheAssertion() {
        String assertion = idJagAssertion();
        TokenRequest request = redemptionRequest(assertion);
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);
        pluginAcceptsAssertion();
        ListAppender<ILoggingEvent> logs = captureLogs();

        Audit audit = auditOfRefusedGrant(request);

        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(EventType.TOKEN_CREATED, audit.getType());
        assertEquals("Assertion client_id does not match the authenticated client. Request: {"
                + "\"ASSERTION_CLIENT_ID\":\"other-agent\","
                + "\"ASSERTION_ISSUER\":\"" + ENTERPRISE_ISSUER + "\","
                + "\"ASSERTION_JTI\":\"jti-1\","
                + "\"GRANT_TYPE\":\"" + GrantType.JWT_BEARER + "\","
                + "\"IDENTITY_PROVIDER\":\"" + IDENTITY_PROVIDER + "\"}", audit.getOutcome().getMessage());
        assertFalse(logs.list.isEmpty());
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(assertion)));
    }

    @Test
    void shouldAuditOnlyTheGrantTypeWhenThePluginRefuses() {
        String assertion = idJagAssertion();
        TokenRequest request = redemptionRequest(assertion);
        request.setScopes(Set.of("calendar.read"));
        request.setResources(Set.of(MCP_SERVER));
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion cannot be parsed")));
        pluginAcceptsAssertion();
        ListAppender<ILoggingEvent> logs = captureLogs();

        Audit audit = auditOfRefusedGrant(request);

        assertEquals("Assertion cannot be parsed. Request: {\"GRANT_TYPE\":\"" + GrantType.JWT_BEARER + "\"}", audit.getOutcome().getMessage());
        assertFalse(logs.list.isEmpty());
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(assertion)));
    }

    @Test
    void shouldKeepVerifiedAssertionContextOnTheRequestWhenAGatewayCheckRefuses() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(request, client, domain).test().assertError(InvalidGrantException.class);

        assertEquals(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "other-agent", null, null, null, null), request.getIdJagAssertionContext());
    }

    @Test
    void shouldLeaveAnEmptyAssertionContextWhenThePluginRefuses() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion verification failed")));

        strategy.process(request, client, domain).test().assertError(InvalidGrantException.class);

        assertEquals(IdJagAssertionContext.empty(), request.getIdJagAssertionContext());
    }

    @Test
    void shouldPropagatePluginRefusalAsInvalidGrantKeepingItsDescription() {
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion issuer is not trusted")));

        strategy.process(jwtBearerRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is not trusted".equals(error.getMessage()));
    }

    @Test
    void shouldRefuseWhenPluginResolvesNoEndUser() {
        when(extensionGrantProvider.resolveEndUser(any())).thenReturn(Maybe.empty());

        strategy.process(jwtBearerRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(InvalidGrantException.class);

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldTargetTheResourceNamedByTheAssertion() {
        givenRedeemableAssertion(verifiedClaims());

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals(Set.of(MCP_SERVER), creationRequest.resources());
    }

    @Test
    void shouldTargetTheRequestedResourceWhenTheAssertionNamesNone() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Parameters.RESOURCE);
        givenRedeemableAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setResources(Set.of(MCP_SERVER));

        TokenCreationRequest creationRequest = strategy.process(request, client, domain).blockingGet();

        assertEquals(Set.of(MCP_SERVER), creationRequest.resources());
    }

    @Test
    void shouldAcceptRequestedResourceEqualToTheAssertionResource() {
        givenRedeemableAssertion(verifiedClaims());
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setResources(Set.of(MCP_SERVER));

        TokenCreationRequest creationRequest = strategy.process(request, client, domain).blockingGet();

        assertEquals(Set.of(MCP_SERVER), creationRequest.resources());
    }

    @Test
    void shouldRefuseWhenNeitherTheAssertionNorTheRequestNamesAResource() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Parameters.RESOURCE);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidTarget("Neither the assertion nor the request names a resource"));
    }

    @Test
    void shouldRefuseWhenTheRequestedResourceDiffersFromTheAssertionResource() {
        givenVerifiedAssertion(verifiedClaims());
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setResources(Set.of(OTHER_MCP_SERVER));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidTarget("Requested resource does not match the assertion resource"));

        verify(protectedResourceManager, never()).getByIdentifier(any());
    }

    @Test
    void shouldRefuseWhenTheRequestNamesSeveralResources() {
        givenVerifiedAssertion(verifiedClaims());
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setResources(Set.of(MCP_SERVER, OTHER_MCP_SERVER));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidTarget("Request must name a single resource"));
    }

    @Test
    void shouldRefuseAssertionResourceThatIsNotASingleIdentifier() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Parameters.RESOURCE, List.of(MCP_SERVER));
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidTarget("Assertion resource must be a single resource identifier"));
    }

    @Test
    void shouldRefuseResourceThatIsNotAProtectedResourceOfThisDomain() {
        givenVerifiedAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidTarget("Resource is not a protected resource of this domain"));
    }

    @Test
    void shouldRefuseRequestedResourceThatIsNotAProtectedResourceOfThisDomain() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Parameters.RESOURCE);
        givenVerifiedAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setResources(Set.of(OTHER_MCP_SERVER));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidTarget("Resource is not a protected resource of this domain"));
    }

    @Test
    void shouldReportClientIdMismatchBeforeResourceChecks() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        claims.remove(Parameters.RESOURCE);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));

        verifyNoInteractions(protectedResourceManager);
    }

    @Test
    void shouldRefuseResourceBeforeBindingTheUser() {
        extensionGrant.setUserExists(true);
        givenVerifiedAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(InvalidResourceException.class);

        verifyNoInteractions(userService, userAuthenticationManager, identityProviderManager);
    }

    @Test
    void shouldRefuseAssertionScopeNotDefinedByTheResourceTools() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read calendar.admin");
        givenRedeemableAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not defined by the resource's MCP tools"));
    }

    @Test
    void shouldRefuseAssertionScopeNotDefinedByTheResourceToolsEvenWhenTheRequestNarrowsItAway() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read calendar.admin");
        givenRedeemableAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.read"));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not defined by the resource's MCP tools"));
    }

    @Test
    void shouldRefuseAssertionScopeTheApplicationDoesNotPermitEvenWhenTheResourceToolsDefineIt() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read mail.send");
        givenRedeemableAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not permitted for the application"));
    }

    @Test
    void shouldRefuseAssertionScopeWhenTheApplicationHasNoScopeSettings() {
        client.setScopeSettings(null);
        givenRedeemableAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not permitted for the application"));
    }

    @Test
    void shouldGrantParameterizedAssertionScopeWhenTheResourceToolsAndTheApplicationHoldItsParameterizedBase() {
        client.setScopeSettings(List.of(new ApplicationScopeSettings("calendar")));
        when(scopeManager.isParameterizedScope("calendar")).thenReturn(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar:team-42");
        givenVerifiedAssertion(claims);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar"));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals(Set.of("calendar:team-42"), creationRequest.scopes());
    }

    @Test
    void shouldRefuseParameterizedAssertionScopeWhenItsBaseIsNotAParameterizedScope() {
        client.setScopeSettings(List.of(new ApplicationScopeSettings("calendar")));
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar:team-42");
        givenVerifiedAssertion(claims);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar"));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not defined by the resource's MCP tools"));
    }

    @Test
    void shouldRefuseParameterizedAssertionScopeWhenTheApplicationDoesNotHoldItsParameterizedBase() {
        when(scopeManager.isParameterizedScope("calendar")).thenReturn(true);
        when(scopeManager.isParameterizedScope("calendar.read")).thenReturn(false);
        when(scopeManager.isParameterizedScope("calendar.write")).thenReturn(false);
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar:team-42");
        givenVerifiedAssertion(claims);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar"));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope is not permitted for the application"));
    }

    @Test
    void shouldRefuseAssertionScopeClaimThatIsNotAString() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, List.of("calendar.read"));
        givenVerifiedAssertion(claims);
        givenRegisteredResource(MCP_SERVER);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(invalidScope("Assertion scope claim must be a space-delimited string"));
    }

    @Test
    void shouldRefuseRequestedScopeOutsideTheAssertionScopes() {
        givenRedeemableAssertion(verifiedClaims());
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.read", "calendar.write"));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidScope("Requested scope exceeds the assertion scopes"));
    }

    @Test
    void shouldRefuseRequestedScopeWhenTheAssertionCarriesNoScopes() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.SCOPE);
        givenRedeemableAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.read"));

        strategy.process(request, client, domain)
                .test()
                .assertError(invalidScope("Requested scope exceeds the assertion scopes"));
    }

    @Test
    void shouldNarrowGrantedScopesToTheRequestedSubset() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read calendar.write");
        givenRedeemableAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.write"));

        TokenCreationRequest creationRequest = strategy.process(request, client, domain).blockingGet();

        assertEquals(Set.of("calendar.write"), creationRequest.scopes());
        assertEquals(Set.of("calendar.write"), request.getIdJagAssertionContext().scopes());
    }

    @Test
    void shouldGrantTheAssertionScopesWhenTheRequestNamesNone() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read calendar.write");
        givenRedeemableAssertion(claims);

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals(Set.of("calendar.read", "calendar.write"), creationRequest.scopes());
    }

    @Test
    void shouldRefuseScopeBeforeBindingTheUser() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.admin");
        givenRedeemableAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(InvalidScopeException.class);

        verifyNoInteractions(userService, userAuthenticationManager, identityProviderManager);
    }

    @Test
    void shouldKeepTheResolvedResourceOnTheRequestContextWhenTheScopeCheckRefuses() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.admin");
        givenRedeemableAssertion(claims);

        strategy.process(request, client, domain).test().assertError(InvalidScopeException.class);

        assertEquals(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "client-id", MCP_SERVER, null, null, null), request.getIdJagAssertionContext());
    }

    @Test
    void shouldIssueAccessTokenAddressedToTheResolvedResourceWithTheGrantedScopes() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.SCOPE, "calendar.read calendar.write");
        givenRedeemableAssertion(claims);
        TokenRequest request = redemptionRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.read"));

        OAuth2Request accessTokenRequest = accessTokenRequestIssuedFor(request);

        assertEquals(Set.of(MCP_SERVER), accessTokenRequest.getResources());
        assertEquals(Set.of("calendar.read"), accessTokenRequest.getScopes());
    }

    @Test
    void shouldIssueAccessTokenWithApplicationDefaultScopesWhenNeitherTheAssertionNorTheRequestCarriesScopes() {
        ApplicationScopeSettings defaultScope = new ApplicationScopeSettings("calendar.read");
        defaultScope.setDefaultScope(true);
        client.setScopeSettings(List.of(defaultScope, new ApplicationScopeSettings("calendar.write")));
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.SCOPE);
        givenRedeemableAssertion(claims);

        OAuth2Request accessTokenRequest = accessTokenRequestIssuedFor(redemptionRequest(idJagAssertion()));

        assertEquals(Set.of(MCP_SERVER), accessTokenRequest.getResources());
        assertEquals(Set.of("calendar.read"), accessTokenRequest.getScopes());
    }

    private OAuth2Request accessTokenRequestIssuedFor(TokenRequest request) {
        TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();
        tokenRequestResolver.setManagers(scopeManager, protectedResourceManager);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());
        when(rulesEngine.fire(any(), any(), any(), eq(client), any())).thenReturn(Single.just(executionContext));
        when(rulesEngine.fire(any(), any(), eq(client), any())).thenReturn(Single.just(executionContext));
        when(tokenService.create(any(), eq(client), any())).thenReturn(Single.just(new AccessToken("access-token")));

        new StrategyGranterAdapter(strategy, domain, tokenService, rulesEngine, tokenRequestResolver, null)
                .grant(request, client)
                .blockingGet();

        ArgumentCaptor<OAuth2Request> accessTokenRequest = ArgumentCaptor.forClass(OAuth2Request.class);
        verify(tokenService).create(accessTokenRequest.capture(), eq(client), any());
        return accessTokenRequest.getValue();
    }

    private Audit auditOfRefusedGrant(TokenRequest request) {
        CompositeTokenGranter tokenGranter = new CompositeTokenGranter();
        ReflectionTestUtils.setField(tokenGranter, "auditService", auditService);
        tokenGranter.addTokenGranter(extensionGrant.getId(), new StrategyGranterAdapter(strategy, domain, tokenService, rulesEngine, null, null));

        tokenGranter.grant(request, client).test().awaitDone(5, TimeUnit.SECONDS).assertError(OAuth2Exception.class);

        ArgumentCaptor<ClientTokenAuditBuilder> audit = ArgumentCaptor.forClass(ClientTokenAuditBuilder.class);
        verify(auditService).report(audit.capture());
        return audit.getValue().build(new ObjectMapper());
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        capturedLogs = new ListAppender<>();
        capturedLogs.start();
        oauth2Logger.setLevel(Level.TRACE);
        oauth2Logger.addAppender(capturedLogs);
        return capturedLogs;
    }

    private void givenVerifiedAssertion(Map<String, Object> verifiedClaims) {
        givenVerifiedAssertion(verifiedClaims, List.of());
    }

    private void givenVerifiedAssertion(Map<String, Object> verifiedClaims, List<UserBindingCriterion> bindingCriteria) {
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        DefaultUser endUser = new DefaultUser((String) verifiedClaims.get(Claims.SUB));
        endUser.setId((String) verifiedClaims.get(Claims.SUB));
        endUser.setAdditionalInformation(new HashMap<>(Map.of(Claims.SUB, verifiedClaims.get(Claims.SUB))));
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(endUser, IDENTITY_PROVIDER, verifiedClaims, bindingCriteria)));
    }

    private void givenRedeemableAssertion(Map<String, Object> verifiedClaims) {
        givenRedeemableAssertion(verifiedClaims, List.of());
    }

    private void givenRedeemableAssertion(Map<String, Object> verifiedClaims, List<UserBindingCriterion> bindingCriteria) {
        givenVerifiedAssertion(verifiedClaims, bindingCriteria);
        givenRegisteredResourceWithToolScopes(MCP_SERVER, Set.of("calendar.read", "calendar.write", "mail.send"));
    }

    private void givenUsersMatchingTheBindingFilter(User... users) {
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of(users)));
    }

    private FilterCriteria bindingFilter() {
        ArgumentCaptor<FilterCriteria> filter = ArgumentCaptor.forClass(FilterCriteria.class);
        verify(userService).findByCriteria(filter.capture());
        return filter.getValue();
    }

    private static FilterCriteria attributeEquals(String attribute, String value) {
        return new FilterCriteria("eq", attribute, value, true, null);
    }

    private static UserBindingCriterion bindingCriterion(String attribute, String expression) {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute(attribute);
        criterion.setExpression(expression);
        return criterion;
    }

    private static User localUser(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private void givenRegisteredResourceWithToolScopes(String identifier, Set<String> toolScopes) {
        givenRegisteredResource(identifier);
        when(protectedResourceManager.getScopesForResources(Set.of(identifier))).thenReturn(toolScopes);
    }

    private void givenRegisteredResource(String identifier) {
        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setResourceIdentifiers(List.of(identifier));
        when(protectedResourceManager.getByIdentifier(identifier)).thenReturn(Set.of(protectedResource));
    }

    private static Predicate<Throwable> refusal(String description) {
        return error -> error instanceof InvalidGrantException && description.equals(error.getMessage());
    }

    private static Predicate<Throwable> invalidTarget(String description) {
        return error -> error instanceof InvalidResourceException && description.equals(error.getMessage());
    }

    private static Predicate<Throwable> invalidScope(String description) {
        return error -> error instanceof InvalidScopeException && description.equals(error.getMessage());
    }

    private static Map<String, Object> verifiedClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ISS, ENTERPRISE_ISSUER);
        claims.put(Claims.SUB, "alice");
        claims.put(Claims.AUD, List.of(DOMAIN_ISSUER));
        claims.put(Claims.CLIENT_ID, "client-id");
        claims.put(Claims.JTI, "jti-1");
        claims.put(Claims.SCOPE, "calendar.read");
        claims.put(Parameters.RESOURCE, MCP_SERVER);
        return claims;
    }

    private static TokenRequest redemptionRequest(String assertion) {
        TokenRequest request = jwtBearerRequest(assertion);
        request.setOrigin(ORIGIN);
        return request;
    }
}
