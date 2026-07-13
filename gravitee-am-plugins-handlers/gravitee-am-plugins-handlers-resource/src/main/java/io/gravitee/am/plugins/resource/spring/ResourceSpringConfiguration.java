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
package io.gravitee.am.plugins.resource.spring;

import io.gravitee.am.common.utils.CappedExecutorFactory;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluatorsRegistry;
import io.gravitee.am.plugins.handlers.api.core.impl.ConfigurationFactoryImpl;
import io.gravitee.am.plugins.handlers.api.core.impl.EvaluatedConfigurationFactoryImpl;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class ResourceSpringConfiguration {

    @Bean
    public ResourcePluginManager resourcePluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory,
            @Value("${executors.shared.maxThreads:20}") int sharedExecutorServiceMaxThreads
            ) {
        ExecutorService executorService = CappedExecutorFactory.newCappedCachedThreadPool("sharedResourceExecutorService", sharedExecutorServiceMaxThreads);
        return new ResourcePluginManager(pluginContextFactory, resourceConfigurationFactory, executorService);
    }

    @Bean
    public ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory(
            PluginConfigurationEvaluatorsRegistry evaluatorsRegistry
    ) {
        return new EvaluatedConfigurationFactoryImpl<>(evaluatorsRegistry.getEvaluators());
    }

}
