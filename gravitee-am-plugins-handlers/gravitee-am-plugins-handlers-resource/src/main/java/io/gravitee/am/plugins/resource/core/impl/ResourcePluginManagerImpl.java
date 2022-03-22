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
package io.gravitee.am.plugins.resource.core.impl;

import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.Resource;
import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.plugin.core.api.PluginContextFactory;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourcePluginManagerImpl extends ResourcePluginManager {

    private final Logger logger = LoggerFactory.getLogger(ResourcePluginManagerImpl.class);

    private final ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory;

    public ResourcePluginManagerImpl(PluginContextFactory pluginContextFactory,
                                     ConfigurationFactory<ResourceConfiguration> resourceConfigurationFactory) {
        super(pluginContextFactory);
        this.resourceConfigurationFactory = resourceConfigurationFactory;
    }

    @Override
    public ResourceProvider create(ProviderConfiguration providerConfiguration) {
        logger.debug("Looking for a resource for [{}]", providerConfiguration.getType());
        Resource resource = instances.get(providerConfiguration.getType());

        if (resource != null) {
            Class<? extends ResourceConfiguration> configurationClass = resource.configuration();
            ResourceConfiguration resourceConfiguration = resourceConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());
            return createProvider(
                    plugins.get(resource),
                    resource.resourceProvider(),
                    List.of(new ResourceConfigurationBeanFactoryPostProcessor(resourceConfiguration))
            );
        } else {
            logger.error("No resource is registered for type {}", providerConfiguration.getType());
            throw new IllegalStateException("No resource is registered for type " + providerConfiguration.getType());
        }
    }
}
