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
package io.gravitee.am.plugins.botdetection.spring;

import io.gravitee.am.botdetection.api.BotDetectionConfiguration;
import io.gravitee.am.plugins.botdetection.core.BotDetectionPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluatorsRegistry;
import io.gravitee.am.plugins.handlers.api.core.impl.EvaluatedConfigurationFactoryImpl;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class BotDetectionSpringConfiguration {

    @Bean
    public BotDetectionPluginManager botDetectionPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<io.gravitee.am.botdetection.api.BotDetectionConfiguration> botDetectionConfigurationFactory
    ) {
        return new BotDetectionPluginManager(pluginContextFactory, botDetectionConfigurationFactory);
    }

    @Bean
    public ConfigurationFactory<BotDetectionConfiguration> botDetectionConfigurationFactory(
            PluginConfigurationEvaluatorsRegistry evaluatorsRegistry
    ) {
        return new EvaluatedConfigurationFactoryImpl<>(evaluatorsRegistry.getEvaluators());
    }

}
