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
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginHandler;
import io.gravitee.plugin.core.api.PluginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantPluginHandler implements PluginHandler {

    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantPluginHandler.class);

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Override
    public boolean canHandle(Plugin plugin) {
        return PluginType.EXTENSION_GRANT.name().equalsIgnoreCase(plugin.type());
    }

    @Override
    public void handle(Plugin plugin) {
        try {
            ClassLoader classloader = pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());

            final Class<?> tokenGranterClass = classloader.loadClass(plugin.clazz());
            LOGGER.info("Register a new extension grant plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(ExtensionGrant.class, tokenGranterClass);

            ExtensionGrant identityIdentityProvider = createInstance((Class<ExtensionGrant>) tokenGranterClass);
            extensionGrantPluginManager.register(new ExtensionGrantDefinition(identityIdentityProvider, plugin));
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create extension grant instance", iae);
        }
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            LOGGER.error("Unable to instantiate class: {}", ex);
            throw ex;
        }
    }
}
