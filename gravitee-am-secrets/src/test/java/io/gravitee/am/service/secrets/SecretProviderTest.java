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
package io.gravitee.am.service.secrets;

import io.gravitee.am.service.secrets.evaluators.SecretsResolvingPluginConfigurationEvaluator;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.am.service.secrets.testsupport.PluginManagerHelper;
import io.gravitee.el.spel.context.SecuredResolver;
import io.gravitee.node.secrets.plugin.mock.MockSecretProviderFactory;
import io.gravitee.node.secrets.plugin.mock.conf.MockSecretProviderConfiguration;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import io.gravitee.node.secrets.plugins.internal.DefaultSecretProviderPlugin;
import io.gravitee.secrets.api.annotation.Secret;
import io.gravitee.secrets.api.plugin.SecretProvider;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.annotation.Nonnull;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        SecretsConfiguration.class,
        SecretProviderTest.AdditionalConfig.class
}, initializers = {
        SecretProviderTest.TestSecretsInitializer.class
})
public class SecretProviderTest {

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private SecretProviderPluginManager pluginManager;

    @Autowired
    private SecretProviderRegistry secretProviderRegistry;

    @Autowired
    private SecretsResolvingPluginConfigurationEvaluator evaluator;

    @BeforeEach
    public void setup() {
        SecuredResolver.initialize(environment);

        pluginManager.register(
                new DefaultSecretProviderPlugin<>(
                        new PluginManagerHelper.MockSecretProviderPlugin(true),
                        MockSecretProviderFactory.class,
                        MockSecretProviderConfiguration.class
                )
        );
    }

    @Test
    public void testSecretProviderRegistry() {
        SecretProvider provider = secretProviderRegistry.get("mock");
        assertThat(provider).isNotNull();
    }

    @Test
    public void testEvaluator() {
        MockPluginConfiguration configuration = new MockPluginConfiguration();
        evaluator.evaluate(configuration);

        assertThat(configuration).isNotNull();
        assertThat(configuration.rawProperty).isEqualTo("rawValue");
        assertThat(configuration.secretProperty).isEqualTo("secretValue");
    }

    @Data
    private static class MockPluginConfiguration {
        private String rawProperty = "rawValue";

        @Secret
        private String secretProperty = "{#secrets.get('/mock/testSecret', 'testSecretKey')}";
    }

    @Configuration
    static class AdditionalConfig {

        @Bean
        public SecretProviderPluginManager pluginManager() {
            return PluginManagerHelper.newPluginManagerWithMockPlugin();
        }
    }

    static class TestSecretsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@Nonnull ConfigurableApplicationContext context) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("test-secrets.yml"));

            Optional.ofNullable(yaml.getObject()).ifPresent(source -> {
                PropertiesPropertySource propertySource = new PropertiesPropertySource("testSecrets", source);
                context.getEnvironment().getPropertySources().addFirst(propertySource);
            });
        }
    }
}
