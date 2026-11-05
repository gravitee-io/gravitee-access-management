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
package io.gravitee.am.gateway.handler.common.auth.idp.impl;

import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IdentityProviderManagerImplTest {

    @InjectMocks
    private IdentityProviderManagerImpl identityProviderManager;

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Test
    public void shouldLoadRemainingIdentityProvidersWhenOneFailsToInitialize() {
        IdentityProvider broken = identityProvider("broken-idp");
        IdentityProvider healthy = identityProvider("healthy-idp");
        when(domain.getId()).thenReturn("domain-id");
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id")).thenReturn(Flowable.just(broken, healthy));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class)))
                .thenThrow(new IllegalArgumentException("invalid well-known URI"))
                .thenReturn(mock(AuthenticationProvider.class));
        when(identityProviderPluginManager.create(eq("oauth2-generic-am-idp"), any(), eq(healthy)))
                .thenReturn(Single.just(Optional.empty()));

        identityProviderManager.afterPropertiesSet();

        assertNull(identityProviderManager.getIdentityProvider("broken-idp"));
        assertSame(healthy, identityProviderManager.getIdentityProvider("healthy-idp"));
        verify(gatewayMetricProvider, times(1)).incrementIdp();
    }

    private static IdentityProvider identityProvider(String id) {
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(id);
        identityProvider.setName(id);
        identityProvider.setType("oauth2-generic-am-idp");
        identityProvider.setConfiguration("{}");
        return identityProvider;
    }
}
