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
package io.gravitee.am.plugins.authenticator.plugin;

import io.gravitee.am.authenticator.api.AuthenticatorPlugin;
import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public class AuthenticatorPluginHandler extends AmPluginHandler<AuthenticatorPlugin<?, ?>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorPluginHandler.class);

    @Override
    public boolean canHandle(Plugin plugin) {
        return "authenticator".equalsIgnoreCase(plugin.type());
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected String type() {
        return "authenticator";
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        try {
            getLogger().info("Register a new plugin: {} [{}]", plugin.id(), plugin.clazz());
            Assert.isAssignable(AuthenticatorPlugin.class, pluginClass);
            pluginManager.register(createInstance(pluginClass, plugin));
        } catch (Exception e) {
            getLogger().error("Unexpected error while create authenticator instance", e);
        }
    }
}
