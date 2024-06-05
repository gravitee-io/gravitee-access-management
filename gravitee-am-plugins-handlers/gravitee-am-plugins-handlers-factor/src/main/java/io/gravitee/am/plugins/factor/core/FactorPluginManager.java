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
package io.gravitee.am.plugins.factor.core;

import io.gravitee.am.factor.api.Factor;
import io.gravitee.am.factor.api.FactorConfiguration;
import io.gravitee.am.factor.api.FactorProvider;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FactorPluginManager
        extends ProviderPluginManager<Factor<?, FactorProvider>, FactorProvider, ProviderConfiguration>
        implements AmPluginManager<Factor<?, FactorProvider>> {

    private final Logger logger = LoggerFactory.getLogger(FactorPluginManager.class);
    private final ConfigurationFactory<FactorConfiguration> configurationFactory;

    public FactorPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<FactorConfiguration> factorConfigurationConfigurationFactory
    ) {
        super(pluginContextFactory);
        this.configurationFactory = factorConfigurationConfigurationFactory;
    }

    @Override
    public FactorProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for a factor for [{}]", providerConfig.getType());
        var factor = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No factor is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No factor is registered for type " + providerConfig.getType());
        });

        var factorConfiguration = configurationFactory.create(factor.configuration(), providerConfig.getConfiguration());
        return createProvider(factor, new FactorConfigurationBeanFactoryPostProcessor(factorConfiguration));
    }


    private static class FactorConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<FactorConfiguration> {
        private FactorConfigurationBeanFactoryPostProcessor(FactorConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
