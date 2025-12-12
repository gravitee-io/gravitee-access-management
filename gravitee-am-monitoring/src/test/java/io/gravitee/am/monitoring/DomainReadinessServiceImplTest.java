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
package io.gravitee.am.monitoring;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DomainReadinessServiceImplTest {

    private DomainReadinessServiceImpl domainReadinessService;

    @Before
    public void setUp() {
        domainReadinessService = new DomainReadinessServiceImpl();
    }

    @Test
    public void shouldSetStateToInitializingWhenInitPluginSyncIsCalled() {
        String domainId = "domain-1";
        String pluginId = "plugin-1";
        String pluginType = "REPORTER";

        domainReadinessService.initPluginSync(domainId, pluginId, pluginType);

        DomainState domainState = domainReadinessService.getDomainState(domainId);
        assertNotNull(domainState);
        assertEquals(DomainState.Status.INITIALIZING, domainState.getStatus());
        assertFalse(domainState.isStable());
    }

    @Test
    public void shouldResetToInitializingWhenNewPluginInitStartsOnDeployedDomain() {
        String domainId = "domain-1";
        
        // 1. Setup domain as DEPLOYED
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);
        
        DomainState initialState = domainReadinessService.getDomainState(domainId);
        assertEquals(DomainState.Status.DEPLOYED, initialState.getStatus());

        // 2. Start init for a plugin
        domainReadinessService.initPluginSync(domainId, "plugin-new", "EXTENSION");
        
        // 3. Verify status changed back to INITIALIZING
        DomainState updatedState = domainReadinessService.getDomainState(domainId);
        assertEquals(DomainState.Status.INITIALIZING, updatedState.getStatus());
    }
}
