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
package io.gravitee.am.gateway.node;

import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.am.gateway.core.upgrader.GatewayUpgraderConfiguration;
import io.gravitee.am.gateway.vertx.VertxEmbeddedContainer;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistryImpl;
import io.gravitee.am.service.purge.ScheduledPurgeService;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.node.api.NodeMetadataResolver;
import io.gravitee.node.container.AbstractNode;
import io.gravitee.plugin.alert.AlertEventProducerManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayNode extends AbstractNode {

    @Autowired
    private NodeMetadataResolver nodeMetadataResolver;

    private Map<String, Object> metadata = null;

    @Override
    public String name() {
        return "Gravitee.io - AM Gateway";
    }

    @Override
    public String application() {
        return "gio-am-gateway";
    }

    @Override
    public List<Class<? extends LifecycleComponent>> components() {
        List<Class<? extends LifecycleComponent>> components = super.components();

        components.add(DataPlaneRegistryImpl.class);
        components.addAll(GatewayUpgraderConfiguration.getComponents());
        components.add(Reactor.class);
        components.add(VertxEmbeddedContainer.class);
        components.add(AlertEventProducerManager.class);
        components.add(ScheduledPurgeService.class);
        return components;
    }

    @Override
    public Map<String, Object> metadata() {
        if(metadata == null) {
            metadata = nodeMetadataResolver.resolve();
        }

        return metadata;
    }
}
