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
package io.gravitee.am.gateway.vertx;

import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.node.api.server.DefaultServerManager;
import io.gravitee.node.api.server.ServerManager;
import io.gravitee.node.vertx.server.VertxServer;
import io.gravitee.node.vertx.server.VertxServerFactory;
import io.gravitee.node.vertx.server.VertxServerOptions;
import io.gravitee.node.vertx.server.http.VertxHttpServer;
import io.gravitee.node.vertx.server.http.VertxHttpServerOptions;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class VertxServerConfiguration {

    private static final String HTTP_PREFIX = "http";

    @Bean
    public ServerManager serverManager(
            VertxServerFactory<VertxServer<?, VertxServerOptions>, VertxServerOptions> serverFactory,
            Environment environment
    ) {
        final DefaultServerManager serverManager = new DefaultServerManager();
        final VertxHttpServerOptions options = VertxHttpServerOptions.builder()
                .prefix(HTTP_PREFIX)
                .environment(environment)
                .port(environment.getProperty(HTTP_PREFIX + ".port", Integer.class, 8092))
                .id(HTTP_PREFIX)
                .build();
        serverManager.register(serverFactory.create(options));

        return serverManager;
    }

    @Bean
    public io.gravitee.am.monitoring.DomainReadinessService domainReadinessService() {
        return new io.gravitee.am.monitoring.DomainReadinessServiceImpl();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GraviteeVerticle graviteeVerticle(
            ServerManager serverManager,
            Reactor reactor) {
        final VertxHttpServer server = serverManager.servers(VertxHttpServer.class).get(0);
        return new GraviteeVerticle(server, reactor);
    }

    @Bean
    public VertxEmbeddedContainer container() {
        return new VertxEmbeddedContainer();
    }
}
