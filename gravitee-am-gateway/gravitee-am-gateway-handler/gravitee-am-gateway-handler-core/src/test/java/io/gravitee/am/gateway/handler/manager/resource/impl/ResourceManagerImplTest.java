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
package io.gravitee.am.gateway.handler.manager.resource.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ResourceEvent;
import io.gravitee.am.gateway.handler.common.license.DomainPluginLicenseGate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.ServiceResourceService;
import io.gravitee.common.event.impl.SimpleEvent;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ResourceManagerImplTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String RESOURCE_ID = "res-1";
    private static final String RESOURCE_TYPE = "ee-resource";

    @Mock
    private ResourcePluginManager resourcePluginManager;

    @Mock
    private Domain domain;

    @Mock
    private EventManager eventManager;

    @Mock
    private ServiceResourceService resourceService;

    @Mock
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Mock
    private DomainReadinessService domainReadinessService;

    @InjectMocks
    private ResourceManagerImpl resourceManager;

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
    }

    private ServiceResource resource() {
        ServiceResource res = new ServiceResource();
        res.setId(RESOURCE_ID);
        res.setType(RESOURCE_TYPE);
        res.setConfiguration("{}");
        return res;
    }

    @Test
    public void shouldClearReadinessForLicensedResourceOnInit() throws Exception {
        when(resourceService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(resource()));
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_RESOURCE, RESOURCE_TYPE, RESOURCE_ID)).thenReturn(true);
        when(resourcePluginManager.create(any())).thenReturn(mock(ResourceProvider.class));

        resourceManager.afterPropertiesSet();

        // a licensed resource that loads clears any stale "unlicensed" readiness recorded by the gate
        verify(domainReadinessService, timeout(2000)).pluginLoaded(DOMAIN_ID, RESOURCE_ID);
        // and is registered with its type so the readiness entry is not left type-less
        verify(domainReadinessService).initPluginSync(DOMAIN_ID, RESOURCE_ID, "RESOURCE");
    }

    @Test
    public void shouldNotLoadOrClearReadinessForUnlicensedResourceOnInit() throws Exception {
        when(resourceService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(resource()));
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_RESOURCE, RESOURCE_TYPE, RESOURCE_ID)).thenReturn(false);

        resourceManager.afterPropertiesSet();

        // wait for the async chain to reach the gate, then assert the plugin was neither created nor cleared
        verify(domainPluginLicenseGate, timeout(2000)).check(PluginLicenseGate.TYPE_RESOURCE, RESOURCE_TYPE, RESOURCE_ID);
        verify(resourcePluginManager, never()).create(any());
        verify(domainReadinessService, never()).pluginLoaded(anyString(), anyString());
    }

    @Test
    public void shouldClearReadinessForLicensedResourceOnReload() throws Exception {
        when(resourceService.findById(RESOURCE_ID)).thenReturn(Maybe.just(resource()));
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_RESOURCE, RESOURCE_TYPE, RESOURCE_ID)).thenReturn(true);
        when(resourcePluginManager.create(any())).thenReturn(mock(ResourceProvider.class));

        resourceManager.onEvent(new SimpleEvent<>(ResourceEvent.UPDATE,
                new Payload(RESOURCE_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.UPDATE)));

        verify(domainReadinessService, timeout(2000)).pluginLoaded(DOMAIN_ID, RESOURCE_ID);
        verify(domainReadinessService).initPluginSync(DOMAIN_ID, RESOURCE_ID, "RESOURCE");
    }

    @Test
    public void shouldRecordFailedResourceOnReload() throws Exception {
        when(resourceService.findById(RESOURCE_ID)).thenReturn(Maybe.just(resource()));
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_RESOURCE, RESOURCE_TYPE, RESOURCE_ID)).thenReturn(true);
        ResourceProvider provider = mock(ResourceProvider.class);
        doThrow(new RuntimeException("boom")).when(provider).start();
        when(resourcePluginManager.create(any())).thenReturn(provider);

        resourceManager.onEvent(new SimpleEvent<>(ResourceEvent.UPDATE,
                new Payload(RESOURCE_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.UPDATE)));

        verify(domainReadinessService, timeout(2000)).pluginFailed(DOMAIN_ID, RESOURCE_ID, "boom");
        verify(domainReadinessService, never()).pluginLoaded(DOMAIN_ID, RESOURCE_ID);
    }
}
