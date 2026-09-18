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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ExtensionGrantEvent;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ExtensionGrantAssertionTypes;
import io.gravitee.am.extensiongrant.api.ProtectedResourceDirectory;
import io.gravitee.am.extensiongrant.api.ProtectedResourceScopes;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.dpop.DPoPProofValidator;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.license.DomainPluginLicenseGate;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.service.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.identityprovider.api.trustedissuer.TrustedIssuerResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantProviderConfiguration;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.plainJwtAssertion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionGrantManagerImplTest {

    private static final String JWT_BEARER_PLUGIN_TYPE = "jwtbearer-am-extension-grant";
    private static final String CROSS_APP_ACCESS_PLUGIN_TYPE = "xaa-am-extension-grant";

    @InjectMocks
    private ExtensionGrantManagerImpl manager;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Mock
    private ExtensionGrantRepository extensionGrantRepository;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private EventManager eventManager;

    @Mock
    private UserGatewayService userService;

    @Mock
    private ScopeManager scopeManager;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private DPoPProofValidator dpopProofValidator;

    @Mock
    private SubjectManager subjectManager;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private AuditService auditService;

    @Mock
    private ExtensionGrantProvider jwtBearerProvider;

    @Mock
    private ExtensionGrantProvider crossAppAccessProvider;

    private final Domain domain = new Domain();
    private final CompositeTokenGranter tokenGranter = new CompositeTokenGranter();
    private final Client client = new Client();

    @BeforeEach
    void setUp() {
        domain.setId("domain-id");
        domain.setName("domain");
        domain.setVersion(DomainVersion.V2_0);
        ReflectionTestUtils.setField(tokenGranter, "auditService", auditService);
        ReflectionTestUtils.setField(manager, "domain", domain);
        ReflectionTestUtils.setField(manager, "tokenGranter", tokenGranter);

        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER));

        lenient().when(domainPluginLicenseGate.check(any(), any(), any())).thenReturn(true);
        lenient().when(jwtBearerProvider.supports(any())).thenAnswer(invocation -> !ExtensionGrantAssertionTypes.isIdJag(invocation.getArgument(0)));
        lenient().when(crossAppAccessProvider.supports(any())).thenAnswer(invocation -> ExtensionGrantAssertionTypes.isIdJag(invocation.getArgument(0)));
        lenient().when(extensionGrantPluginManager.create(any())).thenAnswer(invocation ->
                CROSS_APP_ACCESS_PLUGIN_TYPE.equals(invocation.<ExtensionGrantProviderConfiguration>getArgument(0).getType())
                        ? crossAppAccessProvider
                        : jwtBearerProvider);
    }

    @Test
    void shouldHandBareAuthorizationToTheOldestGrantOnlyWhenJwtBearerIsOlder() {
        givenGrants(jwtBearer("jwt-id", 1000), crossAppAccess("caa-id", 2000));

        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of("jwt-id"), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldHandBareAuthorizationToTheOldestGrantOnlyWhenCrossAppAccessIsOlder() {
        givenGrants(crossAppAccess("caa-id", 1000), jwtBearer("jwt-id", 2000));

        assertEquals(Set.of("caa-id"), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldSelectTheOldestGrantRegardlessOfLoadingOrder() {
        givenGrants(
                jwtBearer("jwt-new", 3000), crossAppAccess("caa-new", 4000),
                jwtBearer("jwt-old", 2000), crossAppAccess("caa-old", 1000));

        assertEquals(Set.of("caa-old"), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldSelectNamedGrantForSuffixedAuthorization() {
        givenGrants(
                jwtBearer("jwt-old", 1000), jwtBearer("jwt-new", 2000),
                crossAppAccess("caa-old", 1000), crossAppAccess("caa-new", 2000));
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~jwt-new", GrantType.JWT_BEARER + "~caa-new"));

        assertEquals(Set.of("caa-new"), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of("jwt-new"), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldKeepBareAuthorizationOnTheOldestGrantWhenANewerGrantIsDeployed() {
        givenGrants(jwtBearer("jwt-id", 1000));
        ExtensionGrant crossAppAccess = crossAppAccess("caa-id", 2000);
        when(extensionGrantRepository.findById("caa-id")).thenReturn(Maybe.just(crossAppAccess));

        manager.onEvent(new SimpleEvent<>(ExtensionGrantEvent.DEPLOY, payload("caa-id", Action.CREATE)));

        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of("jwt-id"), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldHandBareAuthorizationToTheNextOldestGrantWhenTheOldestIsUndeployed() {
        givenGrants(jwtBearer("jwt-old", 1000), crossAppAccess("caa-id", 2000), jwtBearer("jwt-new", 3000));

        manager.onEvent(new SimpleEvent<>(ExtensionGrantEvent.UNDEPLOY, payload("jwt-old", Action.DELETE)));

        assertEquals(Set.of("caa-id"), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldKeepBareAuthorizationOnTheOldestGrantWhenItIsUpdated() {
        givenGrants(jwtBearer("jwt-old", 1000), crossAppAccess("caa-id", 2000));
        ExtensionGrant updatedJwtBearer = jwtBearer("jwt-old", 1000);
        updatedJwtBearer.setUpdatedAt(new Date(5000));
        when(extensionGrantRepository.findById("jwt-old")).thenReturn(Maybe.just(updatedJwtBearer));

        manager.onEvent(new SimpleEvent<>(ExtensionGrantEvent.UPDATE, payload("jwt-old", Action.UPDATE)));

        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of("jwt-old"), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldHandBareAuthorizationToTheNextOldestGrantWhenTheOldestIsUnlicensed() {
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_EXTENSION_GRANT, CROSS_APP_ACCESS_PLUGIN_TYPE, "caa-old"))
                .thenReturn(false);
        givenGrants(crossAppAccess("caa-old", 1000), crossAppAccess("caa-new", 2000), jwtBearer("jwt-id", 3000));

        assertEquals(Set.of("caa-new"), grantsHandling(jwtBearerRequest(idJagAssertion())));
        assertEquals(Set.of(), grantsHandling(jwtBearerRequest(plainJwtAssertion())));
    }

    @Test
    void shouldHandIdJagToCrossAppAccessPlugin() {
        givenGrants(jwtBearer("jwt-id", 1000), crossAppAccess("caa-id", 2000));
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER, GrantType.JWT_BEARER + "~caa-id"));
        givenCrossAppAccessPluginRefuses();

        tokenGranter.grant(jwtBearerRequest(idJagAssertion()), client)
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is not trusted".equals(error.getMessage()));

        verify(jwtBearerProvider, never()).grant(any(), any());
    }

    @Test
    void shouldHandIdJagToCrossAppAccessPluginOnV1Domain() {
        domain.setVersion(DomainVersion.V1_0);
        givenGrants(crossAppAccess("caa-id", 1000));
        givenCrossAppAccessPluginRefuses();

        tokenGranter.grant(jwtBearerRequest(idJagAssertion()), client)
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is not trusted".equals(error.getMessage()));
    }

    @Test
    void shouldGivePluginsIdentityProviderManagerAsTrustedIssuerResolver() {
        givenGrants(crossAppAccess("caa-id", 1000));

        assertSame(identityProviderManager, trustedIssuerResolver());
    }

    @Test
    void shouldGivePluginsTheDomainProtectedResourcesAndScopes() {
        givenGrants(crossAppAccess("caa-id", 1000));
        ProtectedResource protectedResource = new ProtectedResource();
        when(protectedResourceManager.getByIdentifier("https://mcp.example.com/calendar")).thenReturn(Set.of(protectedResource));
        when(protectedResourceManager.getByIdentifier("https://mcp.example.com/mail")).thenReturn(Set.of());
        when(protectedResourceManager.getScopesForResources(Set.of("https://mcp.example.com/calendar"))).thenReturn(Set.of("calendar", "calendar.read"));
        when(scopeManager.isParameterizedScope("calendar")).thenReturn(true);
        when(scopeManager.isParameterizedScope("calendar.read")).thenReturn(false);

        ProtectedResourceDirectory directory = protectedResourceDirectory();

        assertEquals(Optional.of(new ProtectedResourceScopes(Set.of("calendar", "calendar.read"), Set.of("calendar"))),
                directory.findByIdentifier("https://mcp.example.com/calendar"));
        assertTrue(directory.findByIdentifier("https://mcp.example.com/mail").isEmpty());
    }

    @Test
    void shouldHandPlainJwtToJwtBearerPlugin() {
        givenGrants(jwtBearer("jwt-id", 1000), crossAppAccess("caa-id", 2000));
        when(jwtBearerProvider.grant(any(), any())).thenReturn(Maybe.error(new InvalidGrantException("refused by jwt-bearer")));

        tokenGranter.grant(jwtBearerRequest(plainJwtAssertion()), client)
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "refused by jwt-bearer".equals(error.getMessage()));

        verify(crossAppAccessProvider, never()).grant(any(), any());
    }

    @Test
    void shouldRefusePlainJwtWithUnsupportedGrantTypeWhenClientAuthorizesOnlyCrossAppAccess() {
        givenGrants(jwtBearer("jwt-id", 1000), crossAppAccess("caa-id", 2000));
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~caa-id"));

        tokenGranter.grant(jwtBearerRequest(plainJwtAssertion()), client)
                .test()
                .assertError(UnsupportedGrantTypeException.class);

        verify(jwtBearerProvider, never()).grant(any(), any());
        verify(crossAppAccessProvider, never()).grant(any(), any());
    }

    @Test
    void shouldGateCrossAppAccessGrantByPluginTypeLikeAnyOtherGrant() {
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_EXTENSION_GRANT, CROSS_APP_ACCESS_PLUGIN_TYPE, "caa-id"))
                .thenReturn(false);
        givenGrants(crossAppAccess("caa-id", 1000));

        tokenGranter.grant(jwtBearerRequest(idJagAssertion()), client)
                .test()
                .assertError(UnsupportedGrantTypeException.class);

        verify(domainPluginLicenseGate).check(PluginLicenseGate.TYPE_EXTENSION_GRANT, CROSS_APP_ACCESS_PLUGIN_TYPE, "caa-id");
        verifyNoInteractions(extensionGrantPluginManager);
    }

    private void givenCrossAppAccessPluginRefuses() {
        when(crossAppAccessProvider.grant(any(), any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion issuer is not trusted")));
    }

    private TrustedIssuerResolver trustedIssuerResolver() {
        return providerConfiguration().getTrustedIssuerResolver();
    }

    private ProtectedResourceDirectory protectedResourceDirectory() {
        return providerConfiguration().getProtectedResourceDirectory();
    }

    private ExtensionGrantProviderConfiguration providerConfiguration() {
        ArgumentCaptor<ExtensionGrantProviderConfiguration> configuration = ArgumentCaptor.forClass(ExtensionGrantProviderConfiguration.class);
        verify(extensionGrantPluginManager).create(configuration.capture());
        return configuration.getValue();
    }

    private void givenGrants(ExtensionGrant... grants) {
        when(extensionGrantRepository.findByDomain("domain-id")).thenReturn(Flowable.fromArray(grants));
        manager.afterPropertiesSet();
    }

    @SuppressWarnings("unchecked")
    private Set<String> grantsHandling(TokenRequest request) {
        Map<String, TokenGranter> granters = (Map<String, TokenGranter>) ReflectionTestUtils.getField(tokenGranter, "tokenGranters");
        return granters.entrySet().stream()
                .filter(entry -> entry.getValue().handle(request, client))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Payload payload(String grantId, Action action) {
        return new Payload(grantId, ReferenceType.DOMAIN, "domain-id", action);
    }

    private static ExtensionGrant jwtBearer(String id, long createdAt) {
        return grant(id, JWT_BEARER_PLUGIN_TYPE, createdAt);
    }

    private static ExtensionGrant crossAppAccess(String id, long createdAt) {
        return grant(id, CROSS_APP_ACCESS_PLUGIN_TYPE, createdAt);
    }

    private static ExtensionGrant grant(String id, String type, long createdAt) {
        ExtensionGrant grant = new ExtensionGrant();
        grant.setId(id);
        grant.setType(type);
        grant.setGrantType(GrantType.JWT_BEARER);
        grant.setConfiguration("{}");
        grant.setCreatedAt(new Date(createdAt));
        grant.setUpdatedAt(new Date(createdAt));
        return grant;
    }
}
