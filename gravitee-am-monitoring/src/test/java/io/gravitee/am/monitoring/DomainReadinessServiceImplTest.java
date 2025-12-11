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
        domainReadinessService.pluginLoaded(domainId, pluginId);
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);

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
        domainReadinessService.pluginLoaded(domainId, pluginId);
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);
        
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
        domainReadinessService.pluginFailed(domainId, pluginId, "Connection Refused");
        
        DomainState state = domainReadinessService.getDomainState(domainId);
        
        // VERIFY: Plugin is SYNCHRONIZED (we are done trying) but UNSTABLE (it failed)
        assertTrue("Sync state should be true (process finished)", state.getSyncState().get(pluginId));
        assertFalse("Creation state should NOT be success", state.getCreationState().get(pluginId).isSuccess());
        assertEquals("Connection Refused", state.getCreationState().get(pluginId).getMessage());
        
        assertTrue("Domain should be synchronized (logic flow complete)", state.isSynchronized());
        assertFalse("Domain should NOT be stable", state.isStable());
    }

    @Test
    public void shouldMarkPluginLoaded() {
        String domainId = "domain-loaded";
        String pluginId = "plugin-loaded";
        String pluginType = "Loaded Plugin";

        domainReadinessService.initPluginSync(domainId, pluginId, pluginType);
        domainReadinessService.pluginLoaded(domainId, pluginId);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertTrue(state.getSyncState().get(pluginId));
        assertTrue(state.getCreationState().get(pluginId).isSuccess());
    }

    @Test
    public void shouldMarkPluginLoadedWithType() {
        String domainId = "domain-loaded-type";
        String pluginId = "plugin-loaded-type";
        String pluginType = "REPORTER";

        domainReadinessService.initPluginSync(domainId, pluginId, pluginType);
        domainReadinessService.pluginLoaded(domainId, pluginId);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertTrue(state.getSyncState().get(pluginId));
        assertTrue(state.getCreationState().get(pluginId).isSuccess());
        assertEquals(pluginType, state.getCreationState().get(pluginId).getType());
    }

    @Test
    public void shouldMarkPluginFailed() {
        String domainId = "domain-failed-method";
        String pluginId = "plugin-failed-method";
        String pluginType = "Failed Method Plugin";
        String message = "Explicit Failure";

        domainReadinessService.initPluginSync(domainId, pluginId, pluginType);
        domainReadinessService.pluginFailed(domainId, pluginId, message);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertTrue(state.getSyncState().get(pluginId));
        assertFalse(state.getCreationState().get(pluginId).isSuccess());
        assertEquals(message, state.getCreationState().get(pluginId).getMessage());
    }

    @Test
    public void shouldUnloadPlugin() {
        String domainId = "domain-unload";
        String pluginId = "plugin-unload";
        String pluginType = "Unload Plugin";

        // Initialize and Fail
        domainReadinessService.initPluginSync(domainId, pluginId, pluginType);
        domainReadinessService.pluginFailed(domainId, pluginId, "Failure");
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertNotNull(state);
        assertFalse("Should be unstable due to failure", state.isStable());
        assertNotNull(state.getCreationState().get(pluginId));

        // Unload
        domainReadinessService.pluginUnloaded(domainId, pluginId);

        // Verify
        assertTrue("Should be stable after unloading failing plugin", state.isStable());
        assertNull("Plugin should be removed from sync state", state.getSyncState().get(pluginId));
        assertNull("Plugin should be removed from creation state", state.getCreationState().get(pluginId));
    }
}
