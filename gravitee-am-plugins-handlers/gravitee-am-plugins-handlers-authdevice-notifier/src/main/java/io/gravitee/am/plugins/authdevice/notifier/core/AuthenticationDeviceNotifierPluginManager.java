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
package io.gravitee.am.plugins.authdevice.notifier.core;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifier;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierConfiguration;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierPluginManager
        extends ProviderPluginManager<AuthenticationDeviceNotifier, AuthenticationDeviceNotifierProvider, ProviderConfiguration>
        implements AmPluginManager<AuthenticationDeviceNotifier> {

    private final static Logger logger = LoggerFactory.getLogger(AuthenticationDeviceNotifierPluginManager.class);
    private final ConfigurationFactory<AuthenticationDeviceNotifierConfiguration> authDeviceNotifierConfigurationFactory;

    public AuthenticationDeviceNotifierPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<AuthenticationDeviceNotifierConfiguration> authDeviceNotifierConfigurationFactory
    ) {
        super(pluginContextFactory);
        this.authDeviceNotifierConfigurationFactory = authDeviceNotifierConfigurationFactory;
    }

    @Override
    public AuthenticationDeviceNotifierProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for an authentication device notifier for [{}]", providerConfig.getType());
        var authDeviceNotifier = instances.get(providerConfig.getType());

        if (authDeviceNotifier != null) {
            var configurationClass = authDeviceNotifier.configuration();
            var botDetectionConfiguration = authDeviceNotifierConfigurationFactory.create(configurationClass, providerConfig.getConfiguration());

            return createProvider(
                    plugins.get(authDeviceNotifier),
                    authDeviceNotifier.notificationProvider(),
                    List.of(
                            new AuthenticationDeviceNotifierConfigurationBeanFactoryPostProcessor(botDetectionConfiguration)
                    ));
        } else {
            logger.error("No authentication device notifier is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No authentication device notifier is registered for type " + providerConfig.getType());
        }
    }

    private static class AuthenticationDeviceNotifierConfigurationBeanFactoryPostProcessor
            extends NamedBeanFactoryPostProcessor<AuthenticationDeviceNotifierConfiguration> {

        private AuthenticationDeviceNotifierConfigurationBeanFactoryPostProcessor(AuthenticationDeviceNotifierConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
