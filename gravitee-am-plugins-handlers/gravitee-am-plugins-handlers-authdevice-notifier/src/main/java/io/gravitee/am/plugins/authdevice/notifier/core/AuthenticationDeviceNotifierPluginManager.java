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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierPluginManager
        extends ProviderPluginManager<AuthenticationDeviceNotifier<?, AuthenticationDeviceNotifierProvider>, AuthenticationDeviceNotifierProvider, ProviderConfiguration>
        implements AmPluginManager<AuthenticationDeviceNotifier<?, AuthenticationDeviceNotifierProvider>> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationDeviceNotifierPluginManager.class);
    private final ConfigurationFactory<AuthenticationDeviceNotifierConfiguration> configurationFactory;

    public AuthenticationDeviceNotifierPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<AuthenticationDeviceNotifierConfiguration> configurationFactory
    ) {
        super(pluginContextFactory);
        this.configurationFactory = configurationFactory;
    }

    @Override
    public AuthenticationDeviceNotifierProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for an authentication device notifier for [{}]", providerConfig.getType());

        var authDeviceNotifier = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No authentication device notifier is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No authentication device notifier is registered for type " + providerConfig.getType());
        });

        var configuration = configurationFactory.create(authDeviceNotifier.configuration(), providerConfig.getConfiguration());
        return createProvider(
                authDeviceNotifier,
                new AuthDeviceNotifierConfigBeanFactoryPostProcessor(configuration)
        );
    }

    private static class AuthDeviceNotifierConfigBeanFactoryPostProcessor
            extends NamedBeanFactoryPostProcessor<AuthenticationDeviceNotifierConfiguration> {

        private AuthDeviceNotifierConfigBeanFactoryPostProcessor(AuthenticationDeviceNotifierConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
