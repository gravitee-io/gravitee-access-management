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
package io.gravitee.am.management.standalone.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.spring.ServiceConfiguration;
import io.gravitee.am.management.standalone.node.ManagementNode;
import io.gravitee.am.management.standalone.node.ManagementNodeMetadataResolver;
import io.gravitee.am.management.standalone.server.ManagementApiServer;
import io.gravitee.am.password.dictionary.spring.PasswordDictionaryConfiguration;
import io.gravitee.am.plugins.botdetection.spring.BotDetectionConfiguration;
import io.gravitee.am.plugins.certificate.spring.CertificateConfiguration;
import io.gravitee.am.plugins.deviceidentifier.spring.DeviceIdentifierConfiguration;
import io.gravitee.am.plugins.extensiongrant.spring.ExtensionGrantConfiguration;
import io.gravitee.am.plugins.factor.spring.FactorConfiguration;
import io.gravitee.am.plugins.idp.spring.IdentityProviderConfiguration;
import io.gravitee.am.plugins.notifier.spring.NotifierConfiguration;
import io.gravitee.am.plugins.policy.spring.PolicyConfiguration;
import io.gravitee.am.plugins.reporter.spring.ReporterConfiguration;
import io.gravitee.am.plugins.resource.spring.ResourceConfiguration;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.EventManagerImpl;
import io.gravitee.el.ExpressionLanguageInitializer;
import io.gravitee.node.api.NodeMetadataResolver;
import io.gravitee.node.container.NodeFactory;
import io.gravitee.node.vertx.spring.VertxConfiguration;
import io.gravitee.platform.repository.api.RepositoryScopeProvider;
import io.gravitee.plugin.alert.spring.AlertPluginConfiguration;
import io.gravitee.plugin.core.spring.PluginConfiguration;
import io.vertx.reactivex.core.Vertx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        VertxConfiguration.class,
        PluginConfiguration.class,
        ManagementApiServer.class,
        ServiceConfiguration.class,
        io.gravitee.am.service.spring.ServiceConfiguration.class,
        IdentityProviderConfiguration.class,
        CertificateConfiguration.class,
        ExtensionGrantConfiguration.class,
        ReporterConfiguration.class,
        PolicyConfiguration.class,
        NotifierConfiguration.class,
        FactorConfiguration.class,
        ResourceConfiguration.class,
        AlertPluginConfiguration.class,
        BotDetectionConfiguration.class,
        DeviceIdentifierConfiguration.class,
        PasswordDictionaryConfiguration.class
})
public class StandaloneConfiguration {

    @Bean
    public Vertx vertx(io.vertx.core.Vertx vertx) {
        return Vertx.newInstance(vertx);
    }

    @Bean
    public NodeFactory node() {
        return new NodeFactory(ManagementNode.class);
    }

    @Bean
    public EventManager eventManager() {
        return new EventManagerImpl();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ExpressionLanguageInitializer expressionLanguageInitializer() {

        return new ExpressionLanguageInitializer();
    }

    @Bean
    public RepositoryScopeProvider repositoryScopeProvider() {
        return new io.gravitee.am.repository.RepositoryScopeProvider();
    }

    @Bean
    public NodeMetadataResolver nodeMetadataResolver() {
        return new ManagementNodeMetadataResolver();
    }
}
