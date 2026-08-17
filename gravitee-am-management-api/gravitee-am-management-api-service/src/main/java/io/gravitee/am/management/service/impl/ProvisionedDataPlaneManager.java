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

import io.gravitee.am.common.event.DataPlaneEvent;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class ProvisionedDataPlaneManager extends AbstractService<ProvisionedDataPlaneManager> implements EventListener<DataPlaneEvent, Payload> {

    private final DataPlaneRegistry dataPlaneRegistry;
    private final ProvisionedDataPlaneLoader provisionedDataPlaneLoader;
    private final DataPlaneDefinitionRepository dataPlaneDefinitionRepository;
    private final EventManager eventManager;

    public ProvisionedDataPlaneManager(DataPlaneRegistry dataPlaneRegistry,
                                       ProvisionedDataPlaneLoader provisionedDataPlaneLoader,
                                       @Lazy DataPlaneDefinitionRepository dataPlaneDefinitionRepository,
                                       EventManager eventManager) {
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.provisionedDataPlaneLoader = provisionedDataPlaneLoader;
        this.dataPlaneDefinitionRepository = dataPlaneDefinitionRepository;
        this.eventManager = eventManager;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for data plane events for the management API");
        eventManager.subscribeForEvents(this, DataPlaneEvent.class);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        eventManager.unsubscribeForEvents(this, DataPlaneEvent.class);
    }

    @Override
    public void onEvent(Event<DataPlaneEvent, Payload> event) {
        String dataPlaneId = event.content().getId();
        switch (event.type()) {
            case DEPLOY, UPDATE -> reload(dataPlaneId);
            case UNDEPLOY -> undeploy(dataPlaneId);
        }
    }

    private void reload(String dataPlaneId) {
        dataPlaneDefinitionRepository.findById(dataPlaneId)
                .subscribe(
                        this::register,
                        error -> log.error("Data plane [{}] could not be read and will be unavailable until restart", dataPlaneId, error),
                        () -> undeploy(dataPlaneId));
    }

    private void register(DataPlaneDefinition definition) {
        // the node that served the provisioning request reads its own event back
        if (provisionedDataPlaneLoader.isServing(definition)) {
            log.debug("Data plane [{}] is already served at this version, ignoring the event", definition.getId());
            return;
        }
        try {
            dataPlaneRegistry.unregister(definition.getId());
            dataPlaneRegistry.registerProvisioned(provisionedDataPlaneLoader.publish(definition));
            provisionedDataPlaneLoader.markServing(definition);
            log.info("Data plane [{}] of type [{}] registered from a sync event", definition.getId(), definition.getType());
        } catch (Exception e) {
            log.error("Data plane [{}] could not be registered and will be unavailable; domains bound to it cannot be served",
                    definition.getId(), e);
            // otherwise the claim outlives the failed registration and the id stays refused for good
            dataPlaneRegistry.unregister(definition.getId());
        }
    }

    private void undeploy(String dataPlaneId) {
        dataPlaneRegistry.unregister(dataPlaneId);
        provisionedDataPlaneLoader.forget(dataPlaneId);
    }
}
