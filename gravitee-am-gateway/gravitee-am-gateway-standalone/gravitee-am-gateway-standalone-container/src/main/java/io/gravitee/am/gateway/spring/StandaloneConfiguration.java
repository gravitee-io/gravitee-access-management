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
package io.gravitee.am.gateway.spring;

import io.gravitee.am.gateway.node.GatewayNode;
import io.gravitee.am.gateway.reactor.spring.ReactorConfiguration;
import io.gravitee.am.gateway.vertx.VertxConfiguration;
import io.gravitee.am.plugins.certificate.spring.CertificateConfiguration;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.EventManagerImpl;
import io.gravitee.common.node.Node;
import io.gravitee.plugin.core.spring.PluginConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        EnvironmentConfiguration.class,
        PluginConfiguration.class,
        ReactorConfiguration.class,
        VertxConfiguration.class,
        io.gravitee.am.gateway.services.core.spring.ServiceConfiguration.class,
        io.gravitee.am.plugins.idp.spring.IdentityProviderConfiguration.class,
        CertificateConfiguration.class
})
public class StandaloneConfiguration {

    @Bean
    public Node node() {
        return new GatewayNode();
    }

    @Bean
    public EventManager eventManager() {
        return new EventManagerImpl();
    }
}