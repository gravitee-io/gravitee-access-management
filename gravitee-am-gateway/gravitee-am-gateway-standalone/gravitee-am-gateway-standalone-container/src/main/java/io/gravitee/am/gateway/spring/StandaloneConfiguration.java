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
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.utils.JwtSignerExecutor;
import io.gravitee.am.gateway.configuration.ConfigurationChecker;
import io.gravitee.am.gateway.core.upgrader.GatewayUpgraderConfiguration;
import io.gravitee.am.gateway.event.EventManagerImpl;
import io.gravitee.am.gateway.node.GatewayNode;
import io.gravitee.am.gateway.node.GatewayNodeMetadataResolver;
import io.gravitee.am.gateway.reactor.spring.ReactorConfiguration;
import io.gravitee.am.gateway.vertx.VertxServerConfiguration;
import io.gravitee.am.password.dictionary.spring.PasswordDictionaryConfiguration;
import io.gravitee.am.plugins.authdevice.notifier.spring.AuthenticationDeviceNotifierSpringConfiguration;
import io.gravitee.am.plugins.botdetection.spring.BotDetectionSpringConfiguration;
import io.gravitee.am.plugins.certificate.spring.CertificateSpringConfiguration;
import io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistryImpl;
import io.gravitee.am.plugins.dataplane.core.SingleDataPlaneLoader;
import io.gravitee.am.plugins.dataplane.core.SingleDataPlaneProvider;
import io.gravitee.am.plugins.dataplane.spring.DataPlaneSpringConfiguration;
import io.gravitee.am.plugins.deviceidentifier.spring.DeviceIdentifierSpringConfiguration;
import io.gravitee.am.plugins.extensiongrant.spring.ExtensionGrantSpringConfiguration;
import io.gravitee.am.plugins.factor.spring.FactorSpringConfiguration;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.plugins.idp.spring.IdentityProviderSpringConfiguration;
import io.gravitee.am.plugins.policy.spring.PolicySpringConfiguration;
import io.gravitee.am.plugins.protocol.spring.ProtocolSpringConfiguration;
import io.gravitee.am.plugins.reporter.spring.ReporterSpringConfiguration;
import io.gravitee.am.plugins.resource.spring.ResourceSpringConfiguration;
import io.gravitee.am.service.spring.ServiceConfiguration;
import io.gravitee.el.ExpressionLanguageInitializer;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.NodeMetadataResolver;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.plugin.cluster.standalone.StandaloneClusterManager;
import io.gravitee.platform.repository.api.RepositoryScopeProvider;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        ReactorConfiguration.class,
        VertxServerConfiguration.class,
        DataPlaneSpringConfiguration.class,
        GatewayUpgraderConfiguration.class,
        ServiceConfiguration.class,
        IdentityProviderSpringConfiguration.class,
        CertificateSpringConfiguration.class,
        ExtensionGrantSpringConfiguration.class,
        ReporterSpringConfiguration.class,
        ProtocolSpringConfiguration.class,
        PolicySpringConfiguration.class,
        FactorSpringConfiguration.class,
        ResourceSpringConfiguration.class,
        BotDetectionSpringConfiguration.class,
        DeviceIdentifierSpringConfiguration.class,
        PasswordDictionaryConfiguration.class,
        AuthenticationDeviceNotifierSpringConfiguration.class,
})
public class StandaloneConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StandaloneConfiguration.class);
    public static final String GRAVITEE_JWT_EXECUTOR_THREADS = "gravitee.jwt.executor.threads";
    public static final int DEFAULT_JWT_EXECUTOR_THREADS = 20;

    @Bean
    public Node node() {
        return new GatewayNode();
    }

    @Bean
    public EventManager eventManager() {
        return new EventManagerImpl();
    }

    @Bean
    public io.vertx.rxjava3.core.Vertx vertx(@Autowired Vertx vertx) {
        return io.vertx.rxjava3.core.Vertx.newInstance(vertx);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        //Enable ObjectMapper to manage Optional type.
        DatabindCodec.mapper().registerModule(new Jdk8Module());//Manage Optional java type
        //Json.mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);//Reject duplicated keys
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
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
    public ConfigurationChecker configurationChecker() {
        return new ConfigurationChecker();
    }

    @Bean
    public NodeMetadataResolver nodeMetadataResolver() {
        return new GatewayNodeMetadataResolver();
    }

    @Bean
    public ClusterManager clusterManager(io.vertx.rxjava3.core.Vertx vertx) {
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
    public DataPlaneRegistry dataPlaneRegistry(SingleDataPlaneLoader loader, DataPlanePluginManager manager) {
        return new DataPlaneRegistryImpl(loader, manager);
    }

    @Bean
    public SingleDataPlaneProvider singleDataPlaneProvider(DataPlaneRegistry dataPlaneRegistry){
        return new SingleDataPlaneProvider(dataPlaneRegistry);
    }

    @Bean
    public JwtSignerExecutor ioExecutor(Environment environment) {
        int ioThreads = environment.getProperty(GRAVITEE_JWT_EXECUTOR_THREADS, Integer.class, DEFAULT_JWT_EXECUTOR_THREADS);
        log.info("Initializing IO executor for remote signature with {} threads", ioThreads);
        return new JwtSignerExecutor(ioThreads);
    }
}
