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
package io.gravitee.am.management.standalone.node;

import io.gravitee.am.management.service.AlertTriggerManager;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.management.service.AuthorizationEngineManager;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.impl.ClientSecretManager;
import io.gravitee.am.management.service.impl.ProtectedResourceSecretManager;
import io.gravitee.am.management.service.spring.ManagementUpgraderConfiguration;
import io.gravitee.am.management.service.tasks.TasksLoader;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistryImpl;
import io.gravitee.am.service.purge.ScheduledPurgeService;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.node.api.NodeMetadataResolver;
import io.gravitee.node.jetty.node.JettyNode;
import io.gravitee.plugin.alert.AlertEventProducerManager;
import io.gravitee.plugin.alert.AlertTriggerProviderManager;
import io.gravitee.plugin.core.internal.PluginEventListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementNode extends JettyNode {

    @Autowired
    private NodeMetadataResolver nodeMetadataResolver;

    private Map<String, Object> metadata = null;

    @Override
    public String name() {
        return "Gravitee.io - AM Management API";
    }

    @Override
    public String application() {
        return "gio-am-management";
    }

    @Override
    public Map<String, Object> metadata() {
        if (metadata == null) {
            metadata = nodeMetadataResolver.resolve();
        }

        return metadata;
    }

    @Override
    public List<Class<? extends LifecycleComponent>> components() {
        List<Class<? extends LifecycleComponent>> components = super.components();
        components.add(DataPlaneRegistryImpl.class);
        components.addAll(ManagementUpgraderConfiguration.getComponents());
        components.add(PluginEventListener.class);
        components.add(AuditReporterManager.class);
        components.add(IdentityProviderManager.class);
        components.add(CertificateManager.class);
        components.add(AuthorizationEngineManager.class);
        components.add(EmailManager.class);
        components.add(AlertTriggerManager.class);
        components.add(AlertTriggerProviderManager.class);
        components.add(AlertEventProducerManager.class);
        components.add(TasksLoader.class);
        components.add(ClientSecretManager.class);
        components.add(ProtectedResourceSecretManager.class);
        components.add(ScheduledPurgeService.class);
        return components;
    }
}
