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

import io.gravitee.am.service.secrets.providers.Provider;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.common.util.EnvironmentUtils;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import io.gravitee.secrets.api.errors.SecretManagerConfigurationException;
import io.gravitee.secrets.api.plugin.SecretManagerConfiguration;
import io.gravitee.secrets.api.util.ConfigHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class FromConfigurationSecretProviderDeployer  extends AbstractSecretProviderDeployer<Map<String, Object>> {

    public static final String CONFIG_PREFIX = "domains.secrets";

    private final ConfigurableEnvironment environment;

    public FromConfigurationSecretProviderDeployer(
            ConfigurableEnvironment environment,
            SecretProviderRegistry registry,
            SecretProviderPluginManager secretProviderPluginManager
    ) {
        super(registry, secretProviderPluginManager);
        this.environment = environment;
    }

    @Override
    public void init() {
        log.info("loading runtime secret providers from configuration");
        Map<String, Object> allProperties = EnvironmentUtils.getAllProperties(environment);
        Map<String, Object> apiSecrets = ConfigHelper.removePrefix(allProperties, CONFIG_PREFIX);
        int i = 0;
        String provider = provider(i);
        while (apiSecrets.containsKey(provider + ".plugin")) {
            handleProvider(apiSecrets, provider);
            provider = provider(++i);
        }
    }

    protected SecretManagerConfiguration createConfig(
            Map<String, Object> configurationProperties,
            String providerId,
            Class<?> configurationClass
    ) {
        SecretManagerConfiguration config;
        try {
            @SuppressWarnings("unchecked")
            Constructor<SecretManagerConfiguration> constructor =
                    (Constructor<SecretManagerConfiguration>) configurationClass.getDeclaredConstructor(Map.class);
            config = constructor.newInstance(configurationProperties);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new SecretManagerConfigurationException(
                    "Could not create configuration class for secret manager: %s".formatted(providerId),
                    e
            );
        }
        return config;
    }

    private void handleProvider(Map<String, Object> apiSecrets, String provider) {
        Map<String, Object> providerConfig = ConfigHelper.removePrefix(apiSecrets, provider);
        boolean enabled = ConfigHelper.getProperty(providerConfig, "configuration.enabled", Boolean.class, true);
        if (enabled) {
            String plugin = ConfigHelper.getProperty(providerConfig, "plugin", String.class);
            String id = ConfigHelper.getProperty(providerConfig, "id", String.class, plugin);
            deploy(new Provider<>(id, plugin, ConfigHelper.removePrefix(providerConfig, "configuration")));
        }
    }

    private String provider(int i) {
        return "providers[%d]".formatted(i);
    }
}
