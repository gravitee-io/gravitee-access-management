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

package io.gravitee.am.plugins.dataplan.plugin;


import io.gravitee.am.dataplan.api.DataPlan;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.plugin.core.api.AbstractPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class DataPlanPluginHandler extends AbstractPluginHandler {
    private static final String PLUGIN_TYPE = "data-plan";

    @Autowired
    protected PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory;

    @Autowired
    protected PluginManager<DataPlan> pluginManager;

    @Autowired
    protected PluginConfigurationValidatorsRegistry validatorsRegistry;

    @Autowired
    private ApplicationContext appContext;

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            log.info("Register a new plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(DataPlan.class, pluginClass);
            pluginManager.register(createInstance(pluginClass, plugin));
        } catch (Exception iae) {
            log.error("Unexpected error while create bot detection instance", iae);
        }
    }

    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, appContext.getBean("ConnectionProviderFromRepository").getClass().getClassLoader());
    }

    protected DataPlan createInstance(Class<?> pluginClass, Plugin plugin) throws Exception {
        try {
            final DataPlan amPlugin = (DataPlan) pluginClass.getDeclaredConstructor().newInstance();
            amPlugin.setDelegate(plugin);
            return amPlugin;
        } catch (InstantiationException | IllegalAccessException ex) {
            log.error("Unable to instantiate class: {}", pluginClass.getName(), ex);
            throw ex;
        }
    }

    @Override
    protected String type() {
        return PLUGIN_TYPE;
    }
}
