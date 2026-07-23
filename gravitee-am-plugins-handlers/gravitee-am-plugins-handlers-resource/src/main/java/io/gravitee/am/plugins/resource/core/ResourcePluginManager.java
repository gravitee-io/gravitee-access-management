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
package io.gravitee.am.plugins.resource.core;

import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.resource.api.Resource;
import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.plugin.core.api.PluginContextFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class ResourcePluginManager
        extends ProviderPluginManager<Resource<?, ResourceProvider>, ResourceProvider, ProviderConfiguration>
        implements AmPluginManager<Resource<?, ResourceProvider>> {


    private final ConfigurationFactory<ResourceConfiguration> configurationFactory;
    private final ExecutorService executorService;

    public ResourcePluginManager(PluginContextFactory pluginContextFactory,
                                 ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory,
                                 ExecutorService executorService) {
        super(pluginContextFactory);
        this.configurationFactory = resourceConfigurationFactory;
        this.executorService = executorService;
    }

    @Override
    public ResourceProvider create(ProviderConfiguration providerConfig) {
        log.debug("Looking for a resource for [{}]", providerConfig.getType());
        var resource = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            log.error("No resource is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No resource is registered for type " + providerConfig.getType());
        });

        var resourceConfiguration = configurationFactory.create(resource.configuration(), providerConfig.getConfiguration());
        return createProvider(resource, List.of(
                new ResourceConfigurationBeanFactoryPostProcessor(resourceConfiguration),
                new ExecutorServiceBeanFactoryPostProcessor(executorService)));
    }

    private static class ResourceConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ResourceConfiguration> {
        private ResourceConfigurationBeanFactoryPostProcessor(ResourceConfiguration configuration) {
            super("configuration", configuration);
        }
    }

    private static class ExecutorServiceBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ExecutorService> {
        private ExecutorServiceBeanFactoryPostProcessor(ExecutorService configuration) {
            super("sharedExecutorService", configuration);
        }
    }
}
