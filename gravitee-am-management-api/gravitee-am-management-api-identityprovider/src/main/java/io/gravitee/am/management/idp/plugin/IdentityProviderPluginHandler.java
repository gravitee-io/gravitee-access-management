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
package io.gravitee.am.management.idp.plugin;

import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProvider;
import io.gravitee.am.management.idp.core.IdentityProviderDefinition;
import io.gravitee.am.management.idp.core.IdentityProviderPluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginHandler;
import io.gravitee.plugin.core.api.PluginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderPluginHandler implements PluginHandler {

    private final Logger LOGGER = LoggerFactory.getLogger(IdentityProviderPluginHandler.class);

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Override
    public boolean canHandle(Plugin plugin) {
        return plugin.type() == PluginType.IDENTITY_PROVIDER;
    }

    @Override
    public void handle(Plugin plugin) {
        try {
            ClassLoader classloader = pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());

            final Class<?> identityProviderClass = classloader.loadClass(plugin.clazz());
            LOGGER.info("Register a new identity provider plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(IdentityProvider.class, identityProviderClass);

            IdentityProvider identityIdentityProvider = createInstance((Class<IdentityProvider>) identityProviderClass);

            identityProviderPluginManager.register(new IdentityProviderDefinition(identityIdentityProvider, plugin), identityIdentityProvider instanceof OAuth2IdentityProvider);
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create identity provider instance", iae);
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
