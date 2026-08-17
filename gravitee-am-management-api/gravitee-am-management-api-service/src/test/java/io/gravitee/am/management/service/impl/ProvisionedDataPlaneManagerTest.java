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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.DataPlaneEvent;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProvisionedDataPlaneManagerTest {

    private static final DataPlaneDescription DESCRIPTION =
            new DataPlaneDescription("dp-1", "name", "mongodb", "dataPlanes.provisioned.dp-1", "https://gw");

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    @Mock
    private DataPlaneDefinitionRepository dataPlaneDefinitionRepository;

    @Mock
    private EventManager eventManager;

    private ProvisionedDataPlaneManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = new ProvisionedDataPlaneManager(dataPlaneRegistry, provisionedDataPlaneLoader, dataPlaneDefinitionRepository, eventManager);
        when(provisionedDataPlaneLoader.publish(any())).thenReturn(DESCRIPTION);
    }

    @Test
    void shouldSubscribeToDataPlaneEventsOnStart() throws Exception {
        manager.doStart();

        verify(eventManager).subscribeForEvents(manager, DataPlaneEvent.class);
    }

    @Test
    void shouldUnsubscribeOnStop() throws Exception {
        manager.doStart();
        manager.doStop();

        verify(eventManager).unsubscribeForEvents(manager, DataPlaneEvent.class);
    }

    @Test
    void shouldRegisterTheDefinitionOnDeploy() throws Exception {
        DataPlaneDefinition definition = definition();
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition));

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(provisionedDataPlaneLoader).publish(definition);
        verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
    }

    /**
     * The sync poll keeps only the latest event per type and id, so a delete and a re-create of one
     * id inside the poll window arrive as a lone deploy. Registering without dropping the id first
     * would be refused by the registry, which rejects a duplicate id.
     */
    @Test
    void shouldUnregisterBeforeRegisteringOnDeploy() throws Exception {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition()));

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        InOrder inOrder = inOrder(dataPlaneRegistry);
        inOrder.verify(dataPlaneRegistry).unregister("dp-1");
        inOrder.verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
    }

    /**
     * The management sync is not filtered by origin, so the node that served the provisioning request
     * reads its own deploy back. It already holds the provider, and rebuilding would close the pool
     * under any domain already using it.
     */
    @Test
    void shouldIgnoreADeployForTheVersionItAlreadyServes() throws Exception {
        DataPlaneDefinition definition = definition();
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition));
        when(provisionedDataPlaneLoader.isServing(definition)).thenReturn(true);

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(dataPlaneRegistry, never()).unregister(any());
        verify(dataPlaneRegistry, never()).registerProvisioned(any());
        verify(provisionedDataPlaneLoader, never()).publish(any());
    }

    @Test
    void shouldRecordTheVersionItServesOnceRegistered() throws Exception {
        DataPlaneDefinition definition = definition();
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition));

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(provisionedDataPlaneLoader).markServing(definition);
    }

    @Test
    void shouldNotRecordTheVersionWhenTheProviderCannotBeBuilt() throws Exception {
        DataPlaneDefinition definition = definition();
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition));
        doThrow(new IllegalStateException("no provider for type mongodb")).when(dataPlaneRegistry).registerProvisioned(DESCRIPTION);

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(provisionedDataPlaneLoader, never()).markServing(any());
    }

    @Test
    void shouldRegisterTheDefinitionOnUpdate() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition()));

        manager.onEvent(event(DataPlaneEvent.UPDATE, "dp-1"));

        verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
    }

    @Test
    void shouldStopServingTheDataPlaneOnUndeploy() {
        manager.onEvent(event(DataPlaneEvent.UNDEPLOY, "dp-1"));

        verify(dataPlaneRegistry).unregister("dp-1");
        verify(provisionedDataPlaneLoader).forget("dp-1");
        verify(dataPlaneDefinitionRepository, never()).findById(any());
    }

    @Test
    void shouldStopServingTheDataPlaneWhenTheDefinitionHasGoneByTheTimeTheDeployIsRead() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.empty());

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(dataPlaneRegistry).unregister("dp-1");
        verify(provisionedDataPlaneLoader).forget("dp-1");
        verify(dataPlaneRegistry, never()).registerProvisioned(any());
    }

    @Test
    void shouldNotFailWhenTheDefinitionCannotBeRead() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.error(new RuntimeException("connection lost")));

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(dataPlaneRegistry, never()).registerProvisioned(any());
    }

    @Test
    void shouldNotFailWhenTheProviderCannotBeBuilt() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition()));
        doThrow(new IllegalStateException("no provider for type mongodb")).when(dataPlaneRegistry).registerProvisioned(DESCRIPTION);

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
    }

    @Test
    void shouldRegisterTheDataPlaneAsOneThatMustBeVerified() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition()));

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        // register alone would serve the plane unchecked, which only the gravitee.yml ones may do
        verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
        verify(dataPlaneRegistry, never()).register(any());
    }

    @Test
    void shouldDropTheDataPlaneWhenTheProviderCannotBeBuilt() {
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.just(definition()));
        doThrow(new IllegalStateException("no provider for type mongodb")).when(dataPlaneRegistry).registerProvisioned(DESCRIPTION);

        manager.onEvent(event(DataPlaneEvent.DEPLOY, "dp-1"));

        // nothing of the failed registration may outlive it, or the id stays refused for good
        InOrder inOrder = inOrder(dataPlaneRegistry);
        inOrder.verify(dataPlaneRegistry).registerProvisioned(DESCRIPTION);
        inOrder.verify(dataPlaneRegistry).unregister("dp-1");
    }

    private static SimpleEvent<DataPlaneEvent, Payload> event(DataPlaneEvent type, String dataPlaneId) {
        Action action = switch (type) {
            case DEPLOY -> Action.CREATE;
            case UPDATE -> Action.UPDATE;
            case UNDEPLOY -> Action.DELETE;
        };
        return new SimpleEvent<>(type, new Payload(dataPlaneId, ReferenceType.ENVIRONMENT, "DEFAULT", action));
    }

    private static DataPlaneDefinition definition() {
        var definition = new DataPlaneDefinition();
        definition.setId("dp-1");
        definition.setName("Data plane 1");
        definition.setType("mongodb");
        definition.setOrganizationId("DEFAULT");
        definition.setEnvironmentId("DEFAULT");
        definition.setConfiguration("{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}");
        return definition;
    }
}
