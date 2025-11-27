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
package io.gravitee.am.service.secrets.providers.deployer;

import io.gravitee.am.service.secrets.errors.SecretProviderNotFoundException;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.am.service.secrets.testsupport.PluginManagerHelper;
import io.gravitee.node.secrets.plugin.mock.MockSecretProvider;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.util.InMemoryResource;

import java.util.LinkedHashMap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author GraviteeSource Team
 */
public class FromConfigurationSecretProviderDeployerTest {
    InMemoryResource inMemoryResource = new InMemoryResource(
            """
                domains:
                   secrets:
                     providers:
                       - plugin: "mock"
                         configuration:
                            enabled: true
                            secrets:
                              mySecret:
                                redisPassword: "foo"
                                ldapPassword: "bar"
                       - id: "disabled"
                         plugin: "mock"
                         configuration:
                           enabled: false
                """
    );
    private SecretProviderRegistry registry = new SecretProviderRegistry();
    private FromConfigurationSecretProviderDeployer cut;

    @BeforeEach
    void before() {
        final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(inMemoryResource);

        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.getPropertySources().addFirst(new MapPropertySource("test", new LinkedHashMap(yaml.getObject())));

        SecretProviderPluginManager pluginManager = PluginManagerHelper.newPluginManagerWithMockPlugin();
        cut = new FromConfigurationSecretProviderDeployer(mockEnvironment, registry, pluginManager);
    }

    @Test
    public void shouldResolveProvider() {
        cut.init();
        assertThat(registry.get("mock")).isInstanceOf(MockSecretProvider.class);
    }

    @Test
    public void shouldRaiseErrorForDisabledProvider() {
        cut.init();
        assertThrows(SecretProviderNotFoundException.class, () -> registry.get("disabled"));
    }

    @Test
    public void shouldRaiseErrorIfProviderNotFound() {
        cut.init();
        assertThrows(SecretProviderNotFoundException.class, () -> registry.get("unknown"));
    }
}
