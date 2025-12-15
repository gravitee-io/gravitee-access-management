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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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

    @Mock
    private DomainReadinessService domainReadinessService;

    @Test
    public void shouldHandleTopLevelFailure() {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id"))
                .thenReturn(Flowable.error(new RuntimeException("Database error")));

        identityProviderManager.afterPropertiesSet();

        verify(domainReadinessService).pluginInitFailed("domain-id", "IDENTITY_PROVIDER", "Database error");
    }
}
