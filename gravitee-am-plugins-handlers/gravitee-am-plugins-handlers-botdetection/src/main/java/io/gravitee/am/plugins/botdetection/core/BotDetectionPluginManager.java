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
package io.gravitee.am.plugins.botdetection.core;

import io.gravitee.am.botdetection.api.BotDetection;
import io.gravitee.am.botdetection.api.BotDetectionConfiguration;
import io.gravitee.am.botdetection.api.BotDetectionProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionPluginManager
        extends ProviderPluginManager<BotDetection<?, BotDetectionProvider>, BotDetectionProvider, ProviderConfiguration>
        implements AmPluginManager<BotDetection<?, BotDetectionProvider>> {

    private final static Logger logger = LoggerFactory.getLogger(BotDetectionPluginManager.class);
    private final ConfigurationFactory<BotDetectionConfiguration> configurationFactory;

    public BotDetectionPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<BotDetectionConfiguration> botDetectionConfigurationFactory) {
        super(pluginContextFactory);
        this.configurationFactory = botDetectionConfigurationFactory;
    }

    @Override
    public BotDetectionProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for a bot detection for [{}]", providerConfig.getType());

        var botDetection = ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No bot detection is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No bot detection is registered for type " + providerConfig.getType());
        });

        var configuration = configurationFactory.create(botDetection.configuration(), providerConfig.getConfiguration());
        return createProvider(botDetection, new BotDetectionConfigurationBeanFactoryPostProcessor(configuration));
    }

    private static class BotDetectionConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<BotDetectionConfiguration> {
        private BotDetectionConfigurationBeanFactoryPostProcessor(BotDetectionConfiguration bean) {
            super("configuration", bean);
        }
    }
}
