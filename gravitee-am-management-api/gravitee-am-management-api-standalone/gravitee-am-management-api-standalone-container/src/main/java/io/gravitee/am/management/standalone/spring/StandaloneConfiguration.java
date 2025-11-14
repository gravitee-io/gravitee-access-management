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
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.management.service.spring.ManagementServiceConfiguration;
import io.gravitee.am.management.standalone.node.ManagementNode;
import io.gravitee.am.management.standalone.node.ManagementNodeMetadataResolver;
import io.gravitee.am.management.standalone.server.ManagementApiServer;
import io.gravitee.am.password.dictionary.spring.PasswordDictionaryConfiguration;
import io.gravitee.am.plugins.authdevice.notifier.spring.AuthenticationDeviceNotifierSpringConfiguration;
import io.gravitee.am.plugins.authorizationengine.spring.AuthorizationEngineSpringConfiguration;
import io.gravitee.am.plugins.botdetection.spring.BotDetectionSpringConfiguration;
import io.gravitee.am.plugins.certificate.spring.CertificateSpringConfiguration;
import io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistryImpl;
import io.gravitee.am.plugins.dataplane.core.MultiDataPlaneLoader;
import io.gravitee.am.plugins.dataplane.spring.DataPlaneSpringConfiguration;
import io.gravitee.am.plugins.deviceidentifier.spring.DeviceIdentifierSpringConfiguration;
import io.gravitee.am.plugins.extensiongrant.spring.ExtensionGrantSpringConfiguration;
import io.gravitee.am.plugins.factor.spring.FactorSpringConfiguration;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluator;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluatorsRegistry;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.plugins.idp.spring.IdentityProviderSpringConfiguration;
import io.gravitee.am.plugins.notifier.spring.NotifierConfiguration;
import io.gravitee.am.plugins.policy.spring.PolicySpringConfiguration;
import io.gravitee.am.plugins.reporter.spring.ReporterSpringConfiguration;
import io.gravitee.am.plugins.resource.spring.ResourceSpringConfiguration;
import io.gravitee.am.service.secrets.SecretsConfiguration;
import io.gravitee.am.service.spring.ServiceConfiguration;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.EventManagerImpl;
import io.gravitee.el.ExpressionLanguageInitializer;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.NodeMetadataResolver;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.certificates.spring.NodeCertificatesConfiguration;
import io.gravitee.node.plugin.cluster.standalone.StandaloneClusterManager;
import io.gravitee.node.vertx.spring.VertxConfiguration;
import io.gravitee.platform.repository.api.RepositoryScopeProvider;
import io.gravitee.plugin.core.spring.PluginConfiguration;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.List;


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
        DataPlaneSpringConfiguration.class,
        ManagementServiceConfiguration.class,
        ServiceConfiguration.class,
        SecretsConfiguration.class,
        IdentityProviderSpringConfiguration.class,
        AuthorizationEngineSpringConfiguration.class,
        CertificateSpringConfiguration.class,
        ExtensionGrantSpringConfiguration.class,
        ReporterSpringConfiguration.class,
        PolicySpringConfiguration.class,
        NotifierConfiguration.class,
        FactorSpringConfiguration.class,
        ResourceSpringConfiguration.class,
        BotDetectionSpringConfiguration.class,
        DeviceIdentifierSpringConfiguration.class,
        PasswordDictionaryConfiguration.class,
        AuthenticationDeviceNotifierSpringConfiguration.class,
        NodeCertificatesConfiguration.class
})
public class StandaloneConfiguration {

    @Bean
    public Vertx vertx(io.vertx.core.Vertx vertx) {
        return Vertx.newInstance(vertx);
    }

    @Bean
    public Node node() {
        return new ManagementNode();
    }

    @Bean
    @Qualifier("commonEventManager")
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

    @Bean
    public ClusterManager clusterManager(Vertx vertx) {
        return new StandaloneClusterManager(vertx.getDelegate());
    }

    @Bean
    public PropertySourceFallbackConfigurer propertySourceFallbackConfigurer(Environment environment, ApplicationContext applicationContext) {
        return new PropertySourceFallbackConfigurer(environment, applicationContext);
    }

    @Bean
    @Qualifier("EnvironmentWithFallback")
    public RepositoriesEnvironment repositoriesEnvironment(Environment environment){
        return new RepositoriesEnvironment(environment);
    }

    @Bean
    public PluginConfigurationValidatorsRegistry pluginConfigurationValidatorsRegistry(){
        return new PluginConfigurationValidatorsRegistry();
    }

    @Bean
    public PluginConfigurationEvaluatorsRegistry pluginConfigurationEvaluatorsRegistry(List<PluginConfigurationEvaluator> evaluators) {
        return new PluginConfigurationEvaluatorsRegistry(evaluators);
    }

    @Bean
    public DataPlaneRegistry dataPlaneRegistry(MultiDataPlaneLoader loader, DataPlanePluginManager manager) {
        return new DataPlaneRegistryImpl(loader, manager);
    }
}
