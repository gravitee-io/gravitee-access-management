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
import io.gravitee.node.certificates.KeyStoreLoaderManager;
import io.gravitee.node.vertx.ReactivexVertxHttpServerFactory;
import io.gravitee.node.vertx.configuration.HttpServerConfiguration;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class VertxServerConfiguration {

    @Bean("httpServerConfiguration")
    public HttpServerConfiguration httpServerConfiguration(Environment environment) {
        return HttpServerConfiguration.builder().withEnvironment(environment)
                .withDefaultPort(8092)
                .withDefaultMaxFormAttributeSize(2048)
                .build();
    }

    @Bean("gatewayHttpServer")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ReactivexVertxHttpServerFactory vertxHttpServerFactory(
            Vertx vertx,
            @Qualifier("httpServerConfiguration")
            HttpServerConfiguration httpServerConfiguration,
            KeyStoreLoaderManager keyStoreLoaderManager
    ) {
        return new ReactivexVertxHttpServerFactory(vertx, httpServerConfiguration, keyStoreLoaderManager);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GraviteeVerticle graviteeVerticle(
            @Qualifier("gatewayHttpServer") HttpServer httpServer,
            Reactor reactor,
            HttpServerConfiguration httpServerConfiguration) {
        return new GraviteeVerticle(httpServer, reactor, httpServerConfiguration);
    }

    @Bean
    public VertxEmbeddedContainer container() {
        return new VertxEmbeddedContainer();
    }
}
