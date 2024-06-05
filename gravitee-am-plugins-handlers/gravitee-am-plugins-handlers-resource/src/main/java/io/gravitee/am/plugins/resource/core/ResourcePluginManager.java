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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourcePluginManager
        extends ProviderPluginManager<Resource<?, ResourceProvider>, ResourceProvider, ProviderConfiguration>
        implements AmPluginManager<Resource<?, ResourceProvider>> {

    private final Logger logger = LoggerFactory.getLogger(ResourcePluginManager.class);

    private final ConfigurationFactory<ResourceConfiguration> configurationFactory;

    public ResourcePluginManager(PluginContextFactory pluginContextFactory,
                                 ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory) {
        super(pluginContextFactory);
        this.configurationFactory = resourceConfigurationFactory;
    }

    @Override
    public ResourceProvider create(ProviderConfiguration providerConfig) {
        logger.debug("Looking for a resource for [{}]", providerConfig.getType());
        var resource = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No resource is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No resource is registered for type " + providerConfig.getType());
        });

        var resourceConfiguration = configurationFactory.create(resource.configuration(), providerConfig.getConfiguration());
        return createProvider(resource, new ResourceConfigurationBeanFactoryPostProcessor(resourceConfiguration));
    }

    private static class ResourceConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ResourceConfiguration> {
        private ResourceConfigurationBeanFactoryPostProcessor(ResourceConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
