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
package io.gravitee.am.plugins.extensiongrant.plugin;

import io.gravitee.am.extensiongrant.api.ExtensionGrant;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantDefinition;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.plugin.core.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantPluginHandler extends AbstractPluginHandler {

    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantPluginHandler.class);

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            LOGGER.info("Register a new extension grant plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(ExtensionGrant.class, pluginClass);

            var extensionGrant = createInstance((Class<ExtensionGrant>) pluginClass);
            extensionGrantPluginManager.register(new ExtensionGrantDefinition(extensionGrant, plugin));
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create extension grant instance", iae);
        }
    }

    @Override
    protected String type() {
        return PluginType.EXTENSION_GRANT.name();
    }

    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());
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
