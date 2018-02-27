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

import io.gravitee.am.gateway.core.http.HttpServer;
import io.gravitee.am.gateway.jetty.JettyHttpServer;
import io.gravitee.am.gateway.services.ServiceManager;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.node.AbstractNode;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.gravitee.plugin.core.internal.PluginEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayNode extends AbstractNode {

    private final static List<Class<? extends LifecycleComponent>> LIFECYCLE_COMPONENTS = new ArrayList<>();

    static {
        LIFECYCLE_COMPONENTS.add(PluginEventListener.class);
        LIFECYCLE_COMPONENTS.add(PluginRegistry.class);
        LIFECYCLE_COMPONENTS.add(HttpServer.class);
        LIFECYCLE_COMPONENTS.add(ServiceManager.class);
    }

    @Override
    public String name() {
        return "Gravitee.io - Access Management - Gateway";
    }

    @Override
    protected List<Class<? extends LifecycleComponent>> getLifecycleComponents() {
        return LIFECYCLE_COMPONENTS;
    }
}