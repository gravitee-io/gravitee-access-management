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

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

        verifyDomainState(domainId, DomainState.Status.INITIALIZING, false, 1);
    }

    @Test
    public void shouldResetToInitializingWhenNewPluginInitStartsOnDeployedDomain() {
        String domainId = "domain-1";
        
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);
        verifyDomainState(domainId, DomainState.Status.DEPLOYED, true, 0);

        domainReadinessService.initPluginSync(domainId, "plugin-new", "EXTENSION");
        verifyDomainState(domainId, DomainState.Status.INITIALIZING, false, 1);
    }

    @Test
    public void shouldHandleNullsSafely() {
        domainReadinessService.initPluginSync(null, "plugin", "type");
        domainReadinessService.pluginLoaded(null, "plugin");
        domainReadinessService.pluginFailed(null, "plugin", "error");
        domainReadinessService.pluginUnloaded(null, "plugin");
        domainReadinessService.updateDomainStatus(null, DomainState.Status.DEPLOYED);
        domainReadinessService.removeDomain(null);
        
        assertTrue(domainReadinessService.isAllDomainsReady());
        assertTrue(domainReadinessService.getDomainStates().isEmpty());
    }

    @Test
    public void shouldHandlePluginEventsForUnknownDomainOrPlugin() {
        String domainId = "unknown-domain";
        String pluginId = "unknown-plugin";

        domainReadinessService.pluginLoaded(domainId, pluginId);
        assertNull(domainReadinessService.getDomainState(domainId));

        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.INITIALIZING);
        domainReadinessService.pluginLoaded(domainId, pluginId);
        
        verifyDomainState(domainId, DomainState.Status.INITIALIZING, false, 0);

        domainReadinessService.pluginFailed(domainId, pluginId, "error");
        verifyDomainState(domainId, DomainState.Status.INITIALIZING, false, 0);
    }

    @Test
    public void shouldUpdateStateOnPluginLoaded() {
        String domainId = "domain-loaded";
        String pluginId = "plugin-1";
        
        domainReadinessService.initPluginSync(domainId, pluginId, "REPORTER");
        domainReadinessService.pluginLoaded(domainId, pluginId);

        verifyDomainState(domainId, DomainState.Status.DEPLOYED, true, 1);
        DomainState state = domainReadinessService.getDomainState(domainId);
        assertTrue(state.getCreationState().get(pluginId).isSuccess());
    }

    @Test
    public void shouldUpdateStateOnPluginFailed() {
        String domainId = "domain-failed";
        String pluginId = "plugin-1";
        
        domainReadinessService.initPluginSync(domainId, pluginId, "REPORTER");
        domainReadinessService.pluginFailed(domainId, pluginId, "Connection error");

        verifyDomainState(domainId, DomainState.Status.ERROR, false, 1);
        DomainState state = domainReadinessService.getDomainState(domainId);
        assertFalse(state.getCreationState().get(pluginId).isSuccess());
        assertEquals("Connection error", state.getCreationState().get(pluginId).getMessage());
    }

    @Test
    public void shouldHandlePluginUnloaded() {
        String domainId = "domain-unloaded";
        String pluginId = "plugin-1";

        domainReadinessService.initPluginSync(domainId, pluginId, "REPORTER");
        domainReadinessService.pluginUnloaded(domainId, pluginId);

        DomainState state = domainReadinessService.getDomainState(domainId);
        assertNotNull(state);
        assertEquals(0, state.getCreationState().size());
        
        // Ensure graceful handling if domain state is null
        domainReadinessService.pluginUnloaded("new-domain", "plugin-X");
        assertNotNull(domainReadinessService.getDomainState("new-domain"));
    }

    @Test
    public void shouldUpdateDomainStatusCorrectly() {
        String domainId = "domain-status";

        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.INITIALIZING);
        verifyDomainState(domainId, DomainState.Status.INITIALIZING, false, 0);

        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);
        verifyDomainState(domainId, DomainState.Status.DEPLOYED, true, 0);

        // Idempotency check 
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.DEPLOYED);
        verifyDomainState(domainId, DomainState.Status.DEPLOYED, true, 0);
    }

    @Test
    public void shouldRemoveDomain() {
        String domainId = "domain-remove";
        
        domainReadinessService.updateDomainStatus(domainId, DomainState.Status.INITIALIZING);
        assertNotNull(domainReadinessService.getDomainState(domainId));

        domainReadinessService.removeDomain(domainId);
        assertNull(domainReadinessService.getDomainState(domainId));
        
        // Remove non-existent
        domainReadinessService.removeDomain(domainId);
        assertNull(domainReadinessService.getDomainState(domainId));
    }

    @Test
    public void shouldCheckIfAllDomainsReady() {
        assertTrue(domainReadinessService.isAllDomainsReady());

        String d1 = "d1";
        domainReadinessService.updateDomainStatus(d1, DomainState.Status.DEPLOYED);
        assertTrue(domainReadinessService.isAllDomainsReady());

        String d2 = "d2";
        domainReadinessService.initPluginSync(d2, "p1", "type");
        assertFalse(domainReadinessService.isAllDomainsReady());

        domainReadinessService.pluginLoaded(d2, "p1");
        assertTrue(domainReadinessService.isAllDomainsReady());
    }

    @Test
    public void shouldReturnUnmodifiableStatesMap() {
        Map<String, DomainState> states = domainReadinessService.getDomainStates();
        
        try {
            states.put("new", new DomainState());
            assertFalse("Should not be able to modify map", true);
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    private void verifyDomainState(String domainId, DomainState.Status expectedStatus, boolean expectedStable, int expectedPluginCount) {
        DomainState domainState = domainReadinessService.getDomainState(domainId);
        assertNotNull("Domain state should not be null", domainState);
        assertEquals("Status mismatch", expectedStatus, domainState.getStatus());
        assertEquals("Stability mismatch", expectedStable, domainState.isStable());
        if (expectedPluginCount >= 0) {
            assertEquals("Plugin count mismatch", expectedPluginCount, domainState.getCreationState().size());
        }
    }
}
