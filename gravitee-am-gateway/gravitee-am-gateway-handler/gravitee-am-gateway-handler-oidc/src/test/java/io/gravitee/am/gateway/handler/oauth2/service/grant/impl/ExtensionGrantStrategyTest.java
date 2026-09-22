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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ExtensionGrantResult;
import io.gravitee.am.extensiongrant.api.GrantedUserBindingCriterion;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.MultiMap;
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

import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.grantRequest;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ExtensionGrantStrategyTest {

    @Mock
    private ExtensionGrantProvider extensionGrantProvider;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserGatewayService userService;

    @Mock
    private SubjectManager subjectManager;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    private ExtensionGrantStrategy strategy;
    private ExtensionGrant extensionGrant;
    private Domain domain;
    private Client client;
    private TokenRequest tokenRequest;

    @BeforeEach
    void setUp() {
        extensionGrant = new ExtensionGrant();
        extensionGrant.setId("ext-grant-id");
        extensionGrant.setGrantType("urn:ietf:params:oauth:grant-type:jwt-bearer");
        extensionGrant.setCreatedAt(new Date());

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id"));

        tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(new VertxHttpHeaders(MultiMap.caseInsensitiveMultiMap()));

        // V1 mode (without SubjectManager)
        strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                domain,
                openIDDiscoveryService
        );
        strategy.setMinDate(extensionGrant.getCreatedAt());
    }

    @Test
    void shouldSupportExtensionGrantType() {
        when(extensionGrantProvider.supports(any())).thenReturn(true);
        assertTrue(strategy.supports(jwtBearerRequest(null), client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(grantRequest(GrantType.CLIENT_CREDENTIALS), client, domain));
        assertFalse(strategy.supports(grantRequest(GrantType.PASSWORD), client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(jwtBearerRequest(null), client, domain));
    }

    @Test
    void shouldSupportBareGrantTypeWhenOldestExtensionGrant() {
        // Client uses old style (grant type without ID)
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer"));
        when(extensionGrantProvider.supports(any())).thenReturn(true);
        assertTrue(strategy.supports(jwtBearerRequest(null), client, domain));
    }

    @Test
    void shouldSupportRequestThePluginAcceptsWhenClientIsAuthorized() {
        String assertion = idJagAssertion();
        when(extensionGrantProvider.supports(any())).thenReturn(true);

        assertTrue(strategy.supports(jwtBearerRequest(assertion), client, domain));

        verify(extensionGrantProvider).supports(argThat(grantRequest ->
                assertion.equals(grantRequest.parameter(Parameters.ASSERTION))));
    }

    @Test
    void shouldNotSupportRequestThePluginDeclines() {
        when(extensionGrantProvider.supports(any())).thenReturn(false);

        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotAskPluginWhenClientIsNotAuthorized() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));

        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));

        verifyNoInteractions(extensionGrantProvider);
    }

    @Test
    void shouldStillRejectOtherGrantTypesOnRequestAwareSupports() {
        TokenRequest request = jwtBearerRequest(null);
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        assertFalse(strategy.supports(request, client, domain));
        verifyNoInteractions(extensionGrantProvider);
    }

    @Test
    void shouldHandThePluginTheAuthorizationServerIssuerTheRequestedResourcesAndTheApplicationScopes() {
        client.setScopeSettings(List.of(new ApplicationScopeSettings("calendar.read"), new ApplicationScopeSettings("calendar.write")));
        tokenRequest.setOrigin("https://gateway.example.com/domain-b");
        tokenRequest.setResources(Set.of("https://mcp.example.com/calendar", "https://mcp.example.com/mail"));
        when(openIDDiscoveryService.getIssuer("https://gateway.example.com/domain-b")).thenReturn("https://gateway.example.com/domain-b/oidc");
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.empty());

        strategy.process(tokenRequest, client, domain).test().assertNoErrors();

        verify(extensionGrantProvider).grant(argThat(grantRequest ->
                "client-id".equals(grantRequest.getClientId())
                        && "https://gateway.example.com/domain-b/oidc".equals(grantRequest.getAuthorizationServerIssuer())
                        && Set.of("https://mcp.example.com/calendar", "https://mcp.example.com/mail").equals(grantRequest.getRequestedResources())
                        && Set.of("calendar.read", "calendar.write").equals(grantRequest.getApplicationScopes())));
    }

    @Test
    void shouldAttachTheResourceAndScopesThePluginGrants() {
        tokenRequest.setResources(Set.of("https://mcp.example.com/calendar", "https://mcp.example.com/mail"));
        tokenRequest.setScopes(Set.of("calendar.read", "calendar.write"));
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(new ExtensionGrantResult(new DefaultUser("alice"), null, Map.of(), List.of(),
                "https://mcp.example.com/calendar", Set.of("calendar.read"))));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertEquals(Set.of("https://mcp.example.com/calendar"), result.resources());
        assertEquals(Set.of("calendar.read"), result.scopes());
    }

    @Test
    void shouldKeepTheRequestedResourcesAndScopesWhenThePluginGrantsNone() {
        tokenRequest.setResources(Set.of("https://mcp.example.com/calendar"));
        tokenRequest.setScopes(Set.of("calendar.read"));
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(new DefaultUser("alice"))));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertEquals(Set.of("https://mcp.example.com/calendar"), result.resources());
        assertEquals(Set.of("calendar.read"), result.scopes());
    }

    @Test
    void shouldNotSupportRefreshTokenWhenThePluginDoesNot() {
        extensionGrant.setCreateUser(true);
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(new DefaultUser("alice"))));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(new User()));
        when(extensionGrantProvider.supportsRefreshToken()).thenReturn(false);

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertFalse(result.supportRefreshToken());
    }

    @Test
    void shouldFindTheExistingUserByTheVerifiedSubjectAndTheResolvedIdentityProvider() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(verifiedResult(Map.of("sub", "alice"), List.of())));
        when(userService.findByExternalIdAndSource("alice", "idp-id")).thenReturn(Maybe.just(localUser));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertEquals("local-user-id", result.resourceOwner().getId());
        verifyNoInteractions(identityProviderManager);
    }

    @Test
    void shouldRefuseWhenNoUserMatchesTheVerifiedSubject() {
        extensionGrant.setUserExists(true);
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(verifiedResult(Map.of("sub", "alice"), List.of())));
        when(userService.findByExternalIdAndSource("alice", "idp-id")).thenReturn(Maybe.empty());

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(ex -> ex instanceof InvalidGrantException && "No user matches the assertion subject".equals(ex.getMessage()));
    }

    @Test
    void shouldRefuseWithoutLookingUpUsersWhenTheVerifiedClaimsCarryNoSubject() {
        extensionGrant.setUserExists(true);
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(verifiedResult(Map.of("email", "alice@example.com"), List.of())));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(ex -> ex instanceof InvalidGrantException && "No user matches the assertion subject".equals(ex.getMessage()));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldFindTheExistingUserByTheBindingRulesOverTheVerifiedClaims() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(new ExtensionGrantResult(new DefaultUser("alice"), null,
                Map.of("sub", "alice", "email", "alice@example.com"), List.of(new GrantedUserBindingCriterion("emails.value", "{#token['email']}")), null, null)));
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of(localUser)));
        when(userService.enhance(localUser)).thenReturn(Single.just(localUser));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertEquals("local-user-id", result.resourceOwner().getId());
        verify(userService, never()).findByExternalIdAndSource(any(), any());
    }

    @Test
    void shouldRefuseWhenTheBindingRulesMatchSeveralUsers() {
        extensionGrant.setUserExists(true);
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(verifiedResult(Map.of("sub", "alice", "email", "alice@example.com"),
                List.of(new GrantedUserBindingCriterion("emails.value", "{#token['email']}")))));
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of(new User(), new User())));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(ex -> ex instanceof InvalidGrantException && "Several users match the binding rules".equals(ex.getMessage()));
    }

    @Test
    void shouldReportPluginResourceRefusalAsInvalidTarget() {
        when(extensionGrantProvider.grant(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidResourceException("Request must name a single resource")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(ex -> ex instanceof InvalidResourceException && "Request must name a single resource".equals(ex.getMessage()));
    }

    @Test
    void shouldReportPluginScopeRefusalAsInvalidScope() {
        when(extensionGrantProvider.grant(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidScopeException("Assertion scope claim must be a space-delimited string")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(ex -> ex instanceof InvalidScopeException && "Assertion scope claim must be a space-delimited string".equals(ex.getMessage()));
    }

    @Test
    void shouldReturnDefaultErrorMessageWhenEmpty() {
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.error(new RuntimeException("")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(ex -> ex.getMessage().equals("Unknown error"));
    }

    @Test
    void shouldReturnOriginalErrorMessage() {
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.error(new RuntimeException("Custom error message")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(ex -> ex.getMessage().equals("Custom error message"));
    }

    @Test
    void shouldProcessSuccessfullyWithForgedUser() {
        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", result.grantType());
        assertNotNull(result.resourceOwner());
        assertEquals("user-id", result.resourceOwner().getId());
        assertEquals("testuser", result.resourceOwner().getUsername());
        assertFalse(result.supportRefreshToken()); // No createUser/userExists

        assertInstanceOf(GrantData.ExtensionGrantData.class, result.grantData());
        GrantData.ExtensionGrantData data = (GrantData.ExtensionGrantData) result.grantData();
        assertEquals("ext-grant-id", data.extensionGrantId());
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", data.extensionGrantType());
    }

    @Test
    void shouldProcessSuccessfullyWithCreateUser() {
        extensionGrant.setCreateUser(true);
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(extensionGrantProvider.supportsRefreshToken()).thenReturn(true);
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertTrue(result.supportRefreshToken()); // createUser = true
        assertEquals("connected-user-id", result.resourceOwner().getId());
    }

    @Test
    void shouldFailWhenUserExistsWithoutIdp() {
        extensionGrant.setUserExists(true);
        extensionGrant.setIdentityProvider(null);

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(ex -> ex.getMessage().contains("identity_provider"));
    }

    @Test
    void shouldHandleV2ModeWithSubjectManager() {
        // Create V2 mode strategy
        ExtensionGrantStrategy v2Strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put(Claims.GIO_INTERNAL_SUB, "source-id|external-user-id");
        endUser.setAdditionalInformation(additionalInfo);

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(subjectManager.extractUserId("source-id|external-user-id")).thenReturn("external-user-id");
        when(subjectManager.extractSourceId("source-id|external-user-id")).thenReturn("source-id");

        TokenCreationRequest result = v2Strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNotNull(result.resourceOwner());
        assertEquals("external-user-id", result.resourceOwner().getExternalId());
        assertEquals("source-id", result.resourceOwner().getSource());
    }

    @Test
    void shouldProcessSuccessfullyWithEmptyUser() {
        // Extension grant provider returns empty (no user) -> client-only token
        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.empty());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals("client-id", result.clientId());
        assertNull(result.resourceOwner());
        assertFalse(result.supportRefreshToken());

        assertInstanceOf(GrantData.ExtensionGrantData.class, result.grantData());
    }

    // ==================== V2 Mode Tests with CreateUser and UserExists ====================

    @Test
    void shouldHandleV2ModeWithCreateUser() {
        // Create V2 mode strategy with createUser enabled
        extensionGrant.setCreateUser(true);
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));

        ExtensionGrantStrategy v2Strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put(Claims.GIO_INTERNAL_SUB, "source-id|external-user-id");
        endUser.setAdditionalInformation(additionalInfo);

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(extensionGrantProvider.supportsRefreshToken()).thenReturn(true);
        when(subjectManager.extractUserId("source-id|external-user-id")).thenReturn("external-user-id");
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = v2Strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertTrue(result.supportRefreshToken()); // createUser = true with refresh_token grant
        assertNotNull(result.resourceOwner());
        assertEquals("connected-user-id", result.resourceOwner().getId());
    }

    @Test
    void shouldHandleV2ModeWithUserExists() {
        // Create V2 mode strategy with userExists enabled
        extensionGrant.setUserExists(true);
        extensionGrant.setIdentityProvider("idp-id");
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));

        ExtensionGrantStrategy v2Strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");

        DefaultUser idpUser = new DefaultUser("testuser");
        idpUser.setId("idp-user-id");
        idpUser.setAdditionalInformation(new HashMap<>());

        AuthenticationProvider authProvider = org.mockito.Mockito.mock(AuthenticationProvider.class);

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(extensionGrantProvider.supportsRefreshToken()).thenReturn(true);
        when(identityProviderManager.get("idp-id")).thenReturn(Maybe.just(authProvider));
        when(authProvider.loadPreAuthenticatedUser(any())).thenReturn(Maybe.just(idpUser));

        TokenCreationRequest result = v2Strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertTrue(result.supportRefreshToken()); // userExists = true with refresh_token grant
        assertNotNull(result.resourceOwner());
        assertEquals("testuser", result.resourceOwner().getUsername());
    }

    @Test
    void shouldV1ModeNotSetSourceOnForgedUser() {
        // V1 mode (without SubjectManager) should not set source
        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put(Claims.GIO_INTERNAL_SUB, "source-id|external-user-id");
        endUser.setAdditionalInformation(additionalInfo);

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNotNull(result.resourceOwner());
        // V1 mode should not extract source from internal sub
        assertNull(result.resourceOwner().getSource());
        assertNull(result.resourceOwner().getExternalId());
    }

    @Test
    void shouldV2ModeSetSourceOnConnectedUser() {
        extensionGrant.setCreateUser(true);
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));

        ExtensionGrantStrategy v2Strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = v2Strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNotNull(result.resourceOwner());
        // V2 mode should set source to extension grant ID (when no IDP configured)
        assertEquals("ext-grant-id", result.resourceOwner().getSource());
    }

    @Test
    void shouldV2ModeSetSourceToIdpWhenConfigured() {
        extensionGrant.setCreateUser(true);
        extensionGrant.setIdentityProvider("configured-idp-id");
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id", GrantType.REFRESH_TOKEN));

        ExtensionGrantStrategy v2Strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = v2Strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNotNull(result.resourceOwner());
        // V2 mode should set source to IDP ID when configured
        assertEquals("configured-idp-id", result.resourceOwner().getSource());
    }

    @Test
    void shouldNotSupportRefreshTokenWhenClientDoesNotHaveIt() {
        extensionGrant.setCreateUser(true);
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer~ext-grant-id")); // No refresh_token

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(ExtensionGrantResult.endUser(endUser)));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertFalse(result.supportRefreshToken()); // No refresh_token in authorized grants
    }

    @Test
    void shouldNotSupportBareGrantTypeWhenNotOldestExtensionGrant() {
        strategy.setMinDate(new Date(extensionGrant.getCreatedAt().getTime() - 1000));

        // Client uses old style (grant type without ID)
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer"));

        // Should not support because this is not the oldest extension grant
        assertFalse(strategy.supports(jwtBearerRequest(null), client, domain));
    }

    @Test
    void shouldCheckUserAgainstResolvedIdentityProviderWhenNoneConfigured() {
        extensionGrant.setUserExists(true);
        extensionGrant.setIdentityProvider(null);

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        DefaultUser idpUser = new DefaultUser("testuser");
        idpUser.setId("idp-user-id");
        idpUser.setAdditionalInformation(new HashMap<>());
        AuthenticationProvider authProvider = mock(AuthenticationProvider.class);

        when(identityProviderManager.get("resolved-idp")).thenReturn(Maybe.just(authProvider));
        when(authProvider.loadPreAuthenticatedUser(any())).thenReturn(Maybe.just(idpUser));

        TokenCreationRequest result = strategyResolving(endUser, "resolved-idp", null)
                .process(tokenRequest, client, domain)
                .blockingGet();

        assertEquals("testuser", result.resourceOwner().getUsername());
        assertEquals("idp-user-id", result.resourceOwner().getExternalId());
    }

    @Test
    void shouldUseResolvedIdentityProviderAsUserSourceOverConfiguredOne() {
        extensionGrant.setCreateUser(true);
        extensionGrant.setIdentityProvider("configured-idp-id");

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());
        User connectedUser = new User();
        connectedUser.setId("connected-user-id");

        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = strategyResolving(endUser, "resolved-idp", subjectManager)
                .process(tokenRequest, client, domain)
                .blockingGet();

        assertEquals("resolved-idp", result.resourceOwner().getSource());
        assertEquals("resolved-idp", ((GrantData.ExtensionGrantData) result.grantData()).userSource());
        assertEquals("resolved-idp", endUser.getAdditionalInformation().get("source"));
    }

    @Test
    void shouldPreferResolvedIdentityProviderOverConfiguredOneInCheckUserMode() {
        extensionGrant.setUserExists(true);
        extensionGrant.setIdentityProvider("idp-id");

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        DefaultUser idpUser = new DefaultUser("testuser");
        idpUser.setId("idp-user-id");
        idpUser.setAdditionalInformation(new HashMap<>());
        AuthenticationProvider authProvider = mock(AuthenticationProvider.class);

        when(identityProviderManager.get("resolved-idp")).thenReturn(Maybe.just(authProvider));
        when(authProvider.loadPreAuthenticatedUser(any())).thenReturn(Maybe.just(idpUser));

        TokenCreationRequest result = strategyResolving(endUser, "resolved-idp", subjectManager)
                .process(tokenRequest, client, domain)
                .blockingGet();

        assertEquals("resolved-idp", result.resourceOwner().getSource());
        assertEquals("resolved-idp", ((GrantData.ExtensionGrantData) result.grantData()).userSource());
        verify(identityProviderManager, never()).get("idp-id");
    }

    @Test
    void shouldLookUpUserByExternalIdAndResolvedIdentityProviderInV2CheckUserMode() {
        extensionGrant.setUserExists(true);
        extensionGrant.setIdentityProvider(null);

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        DefaultUser idpUser = new DefaultUser("testuser");
        idpUser.setId("idp-user-id");
        idpUser.setAdditionalInformation(new HashMap<>());
        User storedUser = new User();
        storedUser.setUsername("testuser");
        AuthenticationProvider authProvider = mock(AuthenticationProvider.class);

        when(identityProviderManager.get("resolved-idp")).thenReturn(Maybe.just(authProvider));
        when(authProvider.loadPreAuthenticatedUser(any())).thenReturn(Maybe.empty(), Maybe.just(idpUser));
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.empty());
        when(userService.findById("testuser")).thenReturn(Maybe.empty());
        when(userService.findByExternalIdAndSource("testuser", "resolved-idp")).thenReturn(Maybe.just(storedUser));

        TokenCreationRequest result = strategyResolving(endUser, "resolved-idp", subjectManager)
                .process(tokenRequest, client, domain)
                .blockingGet();

        assertEquals("idp-user-id", result.resourceOwner().getExternalId());
        assertEquals("resolved-idp", result.resourceOwner().getSource());
    }

    private ExtensionGrantStrategy strategyResolving(io.gravitee.am.identityprovider.api.User endUser, String identityProvider, SubjectManager subjectManager) {
        when(extensionGrantProvider.grant(any()))
                .thenReturn(Maybe.just(new ExtensionGrantResult(endUser, identityProvider, Map.of(), List.of(), null, null)));
        ExtensionGrantStrategy resolving = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                subjectManager,
                domain,
                openIDDiscoveryService);
        resolving.setMinDate(extensionGrant.getCreatedAt());
        return resolving;
    }

    private static ExtensionGrantResult verifiedResult(Map<String, Object> verifiedClaims, List<GrantedUserBindingCriterion> bindingCriteria) {
        return new ExtensionGrantResult(new DefaultUser("alice"), "idp-id", verifiedClaims, bindingCriteria, null, null);
    }
}
