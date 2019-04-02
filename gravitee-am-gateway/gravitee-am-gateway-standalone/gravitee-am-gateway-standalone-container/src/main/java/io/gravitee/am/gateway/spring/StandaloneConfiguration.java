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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.gravitee.am.gateway.event.EventManagerImpl;
import io.gravitee.am.gateway.node.GatewayNode;
import io.gravitee.am.gateway.reactor.spring.ReactorConfiguration;
import io.gravitee.am.gateway.vertx.VertxServerConfiguration;
import io.gravitee.am.plugins.certificate.spring.CertificateConfiguration;
import io.gravitee.am.plugins.extensiongrant.spring.ExtensionGrantConfiguration;
import io.gravitee.am.plugins.idp.spring.IdentityProviderConfiguration;
import io.gravitee.am.plugins.reporter.spring.ReporterConfiguration;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.gravitee.node.vertx.spring.VertxConfiguration;
import io.gravitee.plugin.core.spring.PluginConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        VertxConfiguration.class,
        ReactorConfiguration.class,
        VertxServerConfiguration.class,
        PluginConfiguration.class,
        IdentityProviderConfiguration.class,
        CertificateConfiguration.class,
        ExtensionGrantConfiguration.class,
        ReporterConfiguration.class
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

    @Bean
    public io.vertx.reactivex.core.Vertx vertx(@Autowired Vertx vertx) {
        return io.vertx.reactivex.core.Vertx.newInstance(vertx);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        //Enable ObjectMapper to manage Optional type.
        Json.mapper.registerModule(new Jdk8Module());//Manage Optional java type
        //Json.mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);//Reject duplicated keys
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
