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
package io.gravitee.am.plugins.dataplane;

import io.gravitee.am.dataplane.api.DataPlane;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.plugin.core.api.AbstractPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

@Slf4j
public class DataPlanePluginHandler extends AbstractPluginHandler {
    private static final String PLUGIN_TYPE = "data-plane";

    @Autowired
    protected PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory;

    @Autowired
    protected PluginManager<DataPlane> pluginManager;

    @Autowired
    protected PluginConfigurationValidatorsRegistry validatorsRegistry;

    @Autowired
    protected ApplicationContext appContext;

    @Override
    protected String type() {
        return PLUGIN_TYPE;
    }
    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }
    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            log.info("Register a new plugin: {} [{}]", plugin.id(), plugin.clazz());
            Assert.isAssignable(DataPlane.class, pluginClass);
            pluginManager.register(createInstance(pluginClass, plugin));
        } catch (Exception iae) {
            log.error("Unexpected error while create data-plane instance", iae);
        }
    }
    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, appContext.getBean("ConnectionProviderFromRepository").getClass().getClassLoader());
    }

    protected DataPlane createInstance(Class<?> pluginClass, Plugin plugin) throws Exception {
        try {
            final DataPlane amPlugin = (DataPlane) pluginClass.getDeclaredConstructor().newInstance();
            amPlugin.setDelegate(plugin);
            return amPlugin;
        } catch (InstantiationException | IllegalAccessException ex) {
            log.error("Unable to instantiate class: {}", pluginClass.getName(), ex);
            throw ex;
        }
    }

}
