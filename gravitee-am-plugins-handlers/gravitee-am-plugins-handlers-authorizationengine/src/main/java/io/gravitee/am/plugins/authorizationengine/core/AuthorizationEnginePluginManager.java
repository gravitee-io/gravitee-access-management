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
package io.gravitee.am.plugins.authorizationengine.core;

import io.gravitee.am.authorizationengine.api.AuthorizationEngine;
import io.gravitee.am.authorizationengine.api.AuthorizationEngineConfiguration;
import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
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
 * @author GraviteeSource Team
 */
public class AuthorizationEnginePluginManager
        extends ProviderPluginManager<AuthorizationEngine<?, AuthorizationEngineProvider>, AuthorizationEngineProvider, ProviderConfiguration>
        implements AmPluginManager<AuthorizationEngine<?, AuthorizationEngineProvider>> {

    private final Logger logger = LoggerFactory.getLogger(AuthorizationEnginePluginManager.class);
    private final ConfigurationFactory<AuthorizationEngineConfiguration> configurationFactory;

    public AuthorizationEnginePluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<AuthorizationEngineConfiguration> configurationFactory
    ) {
        super(pluginContextFactory);
        this.configurationFactory = configurationFactory;
    }

    @Override
    public AuthorizationEngineProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for an authorization engine for [{}]", providerConfig.getType());
        var authEngine = Optional.ofNullable(get(providerConfig.getType())).orElseThrow(() -> new IllegalStateException("No authorization engine is registered for type " + providerConfig.getType()));

        var authEngineConfiguration = configurationFactory.create(authEngine.configuration(), providerConfig.getConfiguration());
        return createProvider(authEngine, new AuthorizationEngineConfigurationBeanFactoryPostProcessor(authEngineConfiguration));
    }

    private static class AuthorizationEngineConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<AuthorizationEngineConfiguration> {
        private AuthorizationEngineConfigurationBeanFactoryPostProcessor(AuthorizationEngineConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
