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
import static org.junit.Assert.*;

public class DomainReadinessServiceImplTest {

    private DomainReadinessServiceImpl domainReadinessService;

    @Before
    public void setUp() {
        domainReadinessService = new DomainReadinessServiceImpl();
    }

    @Test
    public void shouldInitializeAndSyncPlugin() {
        String domainId = "domain-1";
        String pluginId = "plugin-1";
        String pluginName = "Plugin 1";

        // Init sync
        domainReadinessService.initPluginSync(domainId, pluginId, pluginName);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertNotNull(state);
        assertFalse(state.getSyncState().get(pluginId));
        assertFalse(state.isSynchronized());

        // Update with success
        domainReadinessService.updatePluginStatus(domainId, pluginId, pluginName, true, null);

        assertTrue(state.getSyncState().get(pluginId));
        assertTrue(state.getCreationState().get(pluginId).isSuccess());
        assertTrue(state.isSynchronized());
        assertTrue(state.isStable());
    }

    @Test
    public void testSlowInitializationScenario() {
        String domainId = "domain-slow";
        String pluginId = "plugin-slow";
        
        // 1. Event received (Sync Init)
        domainReadinessService.initPluginSync(domainId, pluginId, "Slow Plugin");
        
        // 2. Simulate time passing / processing...
        DomainState state = domainReadinessService.getDomainState(domainId);
        
        // VERIFY: Plugin is KNOWN but NOT SYNCHRONIZED
        assertNotNull("Domain state should exist", state);
        assertFalse("Sync state should be false initially", state.getSyncState().get(pluginId));
        assertFalse("Domain should NOT be synchronized yet", state.isSynchronized());
        
        // 3. Process complete
        domainReadinessService.updatePluginStatus(domainId, pluginId, "Slow Plugin", true, null);
        
        // VERIFY: Plugin is SYNCHRONIZED and STABLE
        assertTrue("Sync state should be true", state.getSyncState().get(pluginId));
        assertTrue("Creation state should be success", state.getCreationState().get(pluginId).isSuccess());
        assertTrue("Domain should be synchronized", state.isSynchronized());
        assertTrue("Domain should be stable", state.isStable());
    }

    @Test
    public void testPluginFailureScenario() {
        String domainId = "domain-fail";
        String pluginId = "plugin-fail";
        
        // 1. Event received
        domainReadinessService.initPluginSync(domainId, pluginId, "Failing Plugin");
        
        // 2. Process failed
        domainReadinessService.updatePluginStatus(domainId, pluginId, "Failing Plugin", false, "Connection Refused");
        
        DomainState state = domainReadinessService.getDomainState(domainId);
        
        // VERIFY: Plugin is SYNCHRONIZED (we are done trying) but UNSTABLE (it failed)
        assertTrue("Sync state should be true (process finished)", state.getSyncState().get(pluginId));
        assertFalse("Creation state should NOT be success", state.getCreationState().get(pluginId).isSuccess());
        assertEquals("Connection Refused", state.getCreationState().get(pluginId).getMessage());
        
        assertTrue("Domain should be synchronized (logic flow complete)", state.isSynchronized());
        assertFalse("Domain should NOT be stable", state.isStable());
    }
}
