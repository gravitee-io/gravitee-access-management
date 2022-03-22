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
package io.gravitee.am.plugins.botdetection.core.impl;

import io.gravitee.am.botdetection.api.BotDetectionConfiguration;
import io.gravitee.am.botdetection.api.BotDetectionProvider;
import io.gravitee.am.plugins.botdetection.core.BotDetectionPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionPluginManagerImpl extends BotDetectionPluginManager {

    private final static Logger logger = LoggerFactory.getLogger(BotDetectionPluginManagerImpl.class);
    private final ConfigurationFactory<BotDetectionConfiguration> botDetectionConfigurationFactory;

    public BotDetectionPluginManagerImpl(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<BotDetectionConfiguration> botDetectionConfigurationFactory) {
        super(pluginContextFactory);
        this.botDetectionConfigurationFactory = botDetectionConfigurationFactory;
    }

    @Override
    public BotDetectionProvider create(ProviderConfiguration providerConfiguration) {
        logger.debug("Looking for a bot detection for [{}]", providerConfiguration.getType());
        var botDetection = instances.get(providerConfiguration.getType());

        if (botDetection != null) {
            var configurationClass = botDetection.configuration();
            var botDetectionConfiguration = botDetectionConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());

            return createProvider(
                    plugins.get(botDetection),
                    botDetection.botDetectionProvider(),
                    List.of(new BotDetectionConfigurationBeanFactoryPostProcessor(botDetectionConfiguration)));
        } else {
            logger.error("No bot detection is registered for type {}", providerConfiguration.getType());
            throw new IllegalStateException("No bot detection is registered for type " + providerConfiguration.getType());
        }
    }
}
