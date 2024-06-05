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
package io.gravitee.am.plugins.handlers.api.plugin;

import com.google.common.reflect.TypeToken;
import io.gravitee.am.common.plugin.AmPlugin;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.plugin.core.api.AbstractPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AmPluginHandler<T extends AmPlugin<?, ?>> extends AbstractPluginHandler {

    @Autowired
    protected PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory;

    @Autowired
    private AmPluginManager<T> pluginManager;
    private final Class<T> actualTypeArgument;

    protected AmPluginHandler() {
        actualTypeArgument = (Class<T>) TypeToken.of(new TypeToken<T>(getClass()){}.getType()).getRawType();
    }

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    protected abstract Logger getLogger();

    protected Class<T> getClazz() {
        return actualTypeArgument;
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            getLogger().info("Register a new plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(getClazz(), pluginClass);
            pluginManager.register(createInstance(pluginClass, plugin));
        } catch (Exception iae) {
            getLogger().error("Unexpected error while create bot detection instance", iae);
        }
    }

    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());
    }

    protected T createInstance(Class<?> pluginClass, Plugin plugin) throws Exception {
        try {
            final T amPlugin = (T) pluginClass.getDeclaredConstructor().newInstance();
            amPlugin.setDelegate(plugin);
            return amPlugin;
        } catch (InstantiationException | IllegalAccessException ex) {
            getLogger().error("Unable to instantiate class: {}", pluginClass.getName(), ex);
            throw ex;
        }
    }
}
