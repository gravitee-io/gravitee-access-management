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
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        tokenRequest.setParameters(new VertxHttpHeaders(new HeadersMultiMap()));

        // V1 mode (without SubjectManager)
        strategy = new ExtensionGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                domain
        );
        strategy.setMinDate(extensionGrant.getCreatedAt());
    }

    @Test
    void shouldSupportExtensionGrantType() {
        assertTrue(strategy.supports("urn:ietf:params:oauth:grant-type:jwt-bearer", client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports("urn:ietf:params:oauth:grant-type:jwt-bearer", client, domain));
    }

    @Test
    void shouldSupportWithMinDateFallback() {
        // Client uses old style (grant type without ID)
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer"));
        assertTrue(strategy.supports("urn:ietf:params:oauth:grant-type:jwt-bearer", client, domain));
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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));

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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));

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
                domain
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put(Claims.GIO_INTERNAL_SUB, "source-id|external-user-id");
        endUser.setAdditionalInformation(additionalInfo);

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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
                domain
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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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
                domain
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");

        DefaultUser idpUser = new DefaultUser("testuser");
        idpUser.setId("idp-user-id");
        idpUser.setAdditionalInformation(new HashMap<>());

        AuthenticationProvider authProvider = org.mockito.Mockito.mock(AuthenticationProvider.class);

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));

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
                domain
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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
                domain
        );
        v2Strategy.setMinDate(extensionGrant.getCreatedAt());

        DefaultUser endUser = new DefaultUser("testuser");
        endUser.setId("user-id");
        endUser.setAdditionalInformation(new HashMap<>());

        User connectedUser = new User();
        connectedUser.setId("connected-user-id");
        connectedUser.setUsername("testuser");

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
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

        when(extensionGrantProvider.grant(any())).thenReturn(Maybe.just(endUser));
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertFalse(result.supportRefreshToken()); // No refresh_token in authorized grants
    }

    @Test
    void shouldNotSupportMinDateFallbackWhenNotOldest() {
        // Set a later minDate so this extension grant is not the oldest
        strategy.setMinDate(new Date(extensionGrant.getCreatedAt().getTime() - 1000));

        // Client uses old style (grant type without ID)
        client.setAuthorizedGrantTypes(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer"));

        // Should not support because this is not the oldest extension grant
        assertFalse(strategy.supports("urn:ietf:params:oauth:grant-type:jwt-bearer", client, domain));
    }
}
