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

import io.gravitee.am.management.core.http.HttpServer;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.management.service.InitializerService;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.node.container.AbstractNode;

import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementNode extends AbstractNode {

    @Override
    public String name() {
        return "Gravitee.io - AM Management API";
    }

    @Override
    public String application() {
        return "gio-am-management";
    }

    @Override
    public List<Class<? extends LifecycleComponent>> components() {
        List<Class<? extends LifecycleComponent>> components = super.components();

        components.add(AuditReporterManager.class);
        components.add(CertificateManager.class);
        components.add(HttpServer.class);
        components.add(InitializerService.class);

        return components;
    }
}
