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
package io.gravitee.am.gateway.handler.manager.authdevice.notifier;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.gateway.handler.common.license.DomainPluginLicenseGate;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.impl.AuthenticationDeviceNotifierManagerImpl;
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.Domain;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationDeviceNotifierManagerTest {

    @InjectMocks
    private AuthenticationDeviceNotifierManagerImpl cut = new AuthenticationDeviceNotifierManagerImpl();

    @Mock
    private Domain domain;

    @Mock
    private AuthenticationDeviceNotifierService deviceNotifierService;

    @Mock
    private AuthenticationDeviceNotifierPluginManager deviceNotifierPluginManager;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Test
    public void shouldLoadRemainingNotifiersWhenOneFailsToInitialize() throws Exception {
        when(domain.getId()).thenReturn("domain-id");
        when(domainPluginLicenseGate.check(any(), any(), any())).thenReturn(true);
        when(deviceNotifierService.findByDomain("domain-id")).thenReturn(Flowable.just(notifier("broken-notifier"), notifier("healthy-notifier")));
        when(deviceNotifierPluginManager.create(any(ProviderConfiguration.class)))
                .thenThrow(new IllegalStateException("invalid configuration"))
                .thenReturn(mock(AuthenticationDeviceNotifierProvider.class));

        cut.afterPropertiesSet();

        verify(domainReadinessService, timeout(5000)).pluginLoaded("domain-id", "healthy-notifier");
        verify(domainReadinessService).pluginFailed("domain-id", "broken-notifier", "invalid configuration");
        verify(domainReadinessService, never()).pluginInitFailed(any(), any(), any());
        assertNull(cut.getAuthDeviceNotifierProvider("broken-notifier"));
        assertNotNull(cut.getAuthDeviceNotifierProvider("healthy-notifier"));
    }

    private static AuthenticationDeviceNotifier notifier(String id) {
        AuthenticationDeviceNotifier notifier = new AuthenticationDeviceNotifier();
        notifier.setId(id);
        notifier.setName(id);
        notifier.setType("http-am-authdevice-notifier");
        notifier.setConfiguration("{}");
        return notifier;
    }
}
