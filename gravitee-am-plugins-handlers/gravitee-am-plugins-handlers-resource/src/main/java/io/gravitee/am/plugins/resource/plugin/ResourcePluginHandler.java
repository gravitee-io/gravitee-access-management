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
package io.gravitee.am.plugins.resource.plugin;

import io.gravitee.am.plugins.resource.core.ResourceDefinition;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.Resource;
import io.gravitee.plugin.core.api.AbstractPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourcePluginHandler extends AbstractPluginHandler {

    private final Logger LOGGER = LoggerFactory.getLogger(ResourcePluginHandler.class);

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private ResourcePluginManager resourcePluginManager;

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected String type() {
        return PluginType.RESOURCE.name();
    }

    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            LOGGER.info("Register a new resource plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(Resource.class, pluginClass);

            var resource = createInstance((Class<Resource>) pluginClass);

            resourcePluginManager.register(new ResourceDefinition(plugin, resource));
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create resource instance", iae);
        }
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            LOGGER.error("Unable to instantiate class: {}", clazz.getName(), ex);
            throw ex;
        }
    }
}
