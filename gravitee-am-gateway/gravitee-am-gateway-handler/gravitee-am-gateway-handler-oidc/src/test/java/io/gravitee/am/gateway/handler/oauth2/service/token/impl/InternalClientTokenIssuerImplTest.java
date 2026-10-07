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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.oauth2.InternalClientTokenIssuer.IssuedToken;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainReadService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalClientTokenIssuerImplTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private DomainReadService domainReadService;

    private final Domain domain = new Domain();

    @Test
    void shouldIssueClientCredentialsTokenWithDefaultScopes() {
        Client client = new Client();
        client.setClientId("pdp-client");
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        ApplicationScopeSettings defaultScope = new ApplicationScopeSettings();
        defaultScope.setScope("pdp:evaluate");
        defaultScope.setDefaultScope(true);
        ApplicationScopeSettings otherScope = new ApplicationScopeSettings();
        otherScope.setScope("other");
        client.setScopeSettings(List.of(defaultScope, otherScope));
        AccessToken accessToken = new AccessToken("token-1");
        accessToken.setExpiresIn(3600);
        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(domainReadService.buildUrl(same(domain), eq(""))).thenReturn("https://am.example.com/domain");

        IssuedToken issuedToken = new InternalClientTokenIssuerImpl(tokenService, domain, domainReadService).issue(client).blockingGet();

        assertEquals(new IssuedToken("token-1", 3600), issuedToken);
        ArgumentCaptor<OAuth2Request> tokenRequest = ArgumentCaptor.forClass(OAuth2Request.class);
        verify(tokenService).create(tokenRequest.capture(), same(client), isNull());
        assertEquals("pdp-client", tokenRequest.getValue().getClientId());
        assertEquals(GrantType.CLIENT_CREDENTIALS, tokenRequest.getValue().getGrantType());
        assertEquals(Set.of("pdp:evaluate"), tokenRequest.getValue().getScopes());
        assertFalse(tokenRequest.getValue().isSupportRefreshToken());
        assertEquals("https://am.example.com/domain", tokenRequest.getValue().getOrigin());
    }

    @Test
    void shouldStripTrailingSlashFromDomainUrl() {
        Client client = new Client();
        client.setClientId("pdp-client");
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(new AccessToken("token-1")));
        when(domainReadService.buildUrl(same(domain), eq(""))).thenReturn("https://am.example.com/domain/");

        new InternalClientTokenIssuerImpl(tokenService, domain, domainReadService).issue(client).blockingGet();

        ArgumentCaptor<OAuth2Request> tokenRequest = ArgumentCaptor.forClass(OAuth2Request.class);
        verify(tokenService).create(tokenRequest.capture(), same(client), isNull());
        assertEquals("https://am.example.com/domain", tokenRequest.getValue().getOrigin());
    }

    @Test
    void shouldRefuseClientWithoutClientCredentialsGrant() {
        Client client = new Client();
        client.setClientId("nocc-client");
        client.setAuthorizedGrantTypes(List.of(GrantType.AUTHORIZATION_CODE));

        new InternalClientTokenIssuerImpl(tokenService, domain, domainReadService).issue(client)
                .test()
                .assertError(UnauthorizedClientException.class);

        verifyNoInteractions(tokenService, domainReadService);
    }

    @Test
    void shouldRefuseClientWithoutGrantTypes() {
        Client client = new Client();
        client.setClientId("nocc-client");
        client.setAuthorizedGrantTypes(null);

        new InternalClientTokenIssuerImpl(tokenService, domain, domainReadService).issue(client)
                .test()
                .assertError(UnauthorizedClientException.class);

        verifyNoInteractions(tokenService, domainReadService);
    }
}
