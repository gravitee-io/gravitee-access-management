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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierPluginManager
        extends ProviderPluginManager<DeviceIdentifier, DeviceIdentifierProvider, ProviderConfiguration>
        implements AmPluginManager<DeviceIdentifier> {

    private final static Logger logger = LoggerFactory.getLogger(DeviceIdentifierPluginManager.class);
    private final ConfigurationFactory<DeviceIdentifierConfiguration> deviceIdentifierConfigurationFactory;

    public DeviceIdentifierPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<DeviceIdentifierConfiguration> deviceIdentifierConfigurationFactory) {
        super(pluginContextFactory);
        this.deviceIdentifierConfigurationFactory = deviceIdentifierConfigurationFactory;
    }

    @Override
    public DeviceIdentifierProvider create(ProviderConfiguration providerConfiguration) {
        logger.debug("Looking for a device identifier for [{}]", providerConfiguration.getType());
        var deviceIdentifier = instances.get(providerConfiguration.getType());

        if (deviceIdentifier != null) {
            Class<? extends DeviceIdentifierConfiguration> configurationClass = deviceIdentifier.configuration();
            var deviceIdentifierConfiguration = deviceIdentifierConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());

            return createProvider(
                    plugins.get(deviceIdentifier),
                    deviceIdentifier.deviceIdentifierProvider(),
                    List.of(
                            new DeviceIdentifierConfigurationBeanFactoryPostProcessor(deviceIdentifierConfiguration)
                    ));
        } else {
            logger.error("No device identifier is registered for type {}", providerConfiguration.getType());
            throw new IllegalStateException("No device identifier is registered for type " + providerConfiguration.getType());
        }
    }

    private class DeviceIdentifierConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<DeviceIdentifierConfiguration> {
        private DeviceIdentifierConfigurationBeanFactoryPostProcessor(DeviceIdentifierConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
