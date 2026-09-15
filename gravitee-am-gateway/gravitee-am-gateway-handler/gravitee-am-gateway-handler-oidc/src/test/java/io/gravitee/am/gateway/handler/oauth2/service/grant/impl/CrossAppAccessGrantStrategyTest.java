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

import io.gravitee.am.common.oauth2.ExtensionGrantPluginType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.plainJwtAssertion;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossAppAccessGrantStrategyTest {

    @Mock
    private ExtensionGrantProvider extensionGrantProvider;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserGatewayService userService;

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

        strategy = new CrossAppAccessGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                null,
                domain);
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
    void shouldHandAssertionToPluginThenRefuseVerifiedAssertionUntilRedemptionIsImplemented() {
        String assertion = idJagAssertion();
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(new DefaultUser("alice"), "idp-id")));

        strategy.process(jwtBearerRequest(assertion), client, domain)
                .test()
                .assertError(InvalidGrantException.class);

        verify(extensionGrantProvider).resolveEndUser(argThat(pluginRequest ->
                assertion.equals(pluginRequest.getRequestParameters().get(Parameters.ASSERTION))
                        && "client-id".equals(pluginRequest.getClientId())));
        verify(extensionGrantProvider, never()).grant(any());
        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
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
}
