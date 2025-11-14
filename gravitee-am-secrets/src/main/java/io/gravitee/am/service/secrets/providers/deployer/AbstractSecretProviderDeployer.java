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
import io.gravitee.node.secrets.plugins.SecretProviderPlugin;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import io.gravitee.secrets.api.errors.SecretProviderNotFoundException;
import io.gravitee.secrets.api.plugin.SecretManagerConfiguration;
import io.gravitee.secrets.api.plugin.SecretProvider;
import io.gravitee.secrets.api.plugin.SecretProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSecretProviderDeployer<C> implements SecretProviderDeployer<C> {

    private final SecretProviderRegistry registry;
    private final SecretProviderPluginManager secretProviderPluginManager;

    @Override
    public void deploy(Provider<C> provider) {
        String providerId = provider.id();
        String pluginId = provider.plugin();

        try {
            log.info("Deploying secret provider [{}] of type [{}]...", providerId, pluginId);
            final SecretProviderPlugin<?, ?> secretProviderPlugin = secretProviderPluginManager.get(pluginId);
            if (secretProviderPlugin == null) {
                log.error("No secret-provider with id [{}] found in Gravitee configuration", pluginId);
                return;
            }
            final Class<? extends SecretManagerConfiguration> configurationClass = secretProviderPlugin.configuration();
            final SecretProviderFactory<SecretManagerConfiguration> factory = secretProviderPluginManager.getFactoryById(pluginId);
            if (configurationClass != null && factory != null) {
                // read the config using the plugin class loader
                SecretManagerConfiguration config = createConfig(
                        provider.config(),
                        providerId,
                        // actually uses the plugin class loader to load the class
                        factory.getClass().getClassLoader().loadClass(configurationClass.getName())
                );
                log.info("Secret provider [{}] of type [{}]: DEPLOYED", providerId, pluginId);
                SecretProvider secretProvider = factory.create(config).start();
                this.registry.register(providerId, secretProvider);
            } else {
                log.info("Secret provider [{}] of type [{}]: FAILED", providerId, pluginId);
                throw new SecretProviderNotFoundException("Cannot find secret provider [%s] plugin".formatted(pluginId));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot load secret-provider plugin [%s]".formatted(pluginId), e);
        }
    }

    protected abstract SecretManagerConfiguration createConfig(C configurationData, String providerId, Class<?> configurationClass);
}
