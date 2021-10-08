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
package io.gravitee.am.plugins.deviceidentifier.plugin;

import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierDefinition;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierPluginManager;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifier;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierPluginHandler implements PluginHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(DeviceIdentifierPluginHandler.class);
    public static final String PLUGIN_TYPE_DEVICE_IDENTIFIER = "DEVICE_IDENTIFIER";

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private DeviceIdentifierPluginManager pluginManager;

    @Override
    public boolean canHandle(Plugin plugin) {
        return PLUGIN_TYPE_DEVICE_IDENTIFIER.equalsIgnoreCase(plugin.type());
    }

    @Override
    public void handle(Plugin plugin) {
        try {
            ClassLoader classloader = pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());

            final Class<?> pluginClass = classloader.loadClass(plugin.clazz());
            LOGGER.info("Register a new device identifier plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(DeviceIdentifier.class, pluginClass);

            DeviceIdentifier deviceIdentifier = createInstance((Class<DeviceIdentifier>) pluginClass);

            pluginManager.register(new DeviceIdentifierDefinition(deviceIdentifier, plugin));
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create device identifier instance", iae);
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
