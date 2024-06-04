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
package io.gravitee.am.plugins.deviceidentifier.core;

import io.gravitee.am.deviceidentifier.api.DeviceIdentifier;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierConfiguration;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierPluginManager
        extends ProviderPluginManager<DeviceIdentifier<?, DeviceIdentifierProvider>, DeviceIdentifierProvider, ProviderConfiguration>
        implements AmPluginManager<DeviceIdentifier<?, DeviceIdentifierProvider>> {

    private static final Logger logger = LoggerFactory.getLogger(DeviceIdentifierPluginManager.class);
    private final ConfigurationFactory<DeviceIdentifierConfiguration> configurationFactory;

    public DeviceIdentifierPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<DeviceIdentifierConfiguration> configurationFactory) {
        super(pluginContextFactory);
        this.configurationFactory = configurationFactory;
    }

    @Override
    public DeviceIdentifierProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for a device identifier for [{}]", providerConfig.getType());
        var deviceIdentifier = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No device identifier is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No device identifier is registered for type " + providerConfig.getType());
        });

        var configuration = configurationFactory.create(deviceIdentifier.configuration(), providerConfig.getConfiguration());
        return createProvider(deviceIdentifier, new DeviceIdentifierConfigurationBeanFactoryPostProcessor(configuration));
    }

    private static class DeviceIdentifierConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<DeviceIdentifierConfiguration> {
        private DeviceIdentifierConfigurationBeanFactoryPostProcessor(DeviceIdentifierConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
