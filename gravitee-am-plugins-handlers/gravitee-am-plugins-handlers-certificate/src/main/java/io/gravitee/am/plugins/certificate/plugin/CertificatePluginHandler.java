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
package io.gravitee.am.plugins.certificate.plugin;

import io.gravitee.am.certificate.api.Certificate;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificatePluginHandler extends AmPluginHandler<Certificate<?, ?>> {

    private final Logger LOGGER = LoggerFactory.getLogger(CertificatePluginHandler.class);

    @Autowired
    private PluginConfigurationValidatorsRegistry validatorsRegistry;

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected void handle(Plugin plugin, Class<?> pluginClass) {
        super.handle(plugin, pluginClass);
        if (pluginManager.findById(plugin.id()) != null) {
            registerValidator(plugin);
        }
    }

    private void registerValidator(Plugin plugin){
        try {
            getLogger().info("Register a new plugin validator: {} [{}]", plugin.id(), plugin.clazz());
            validatorsRegistry.put(new PluginConfigurationValidator(plugin.id(), pluginManager.getSchema(plugin.id()), new JsonSchemaValidatorImpl()));
        } catch (Exception iae) {
            getLogger().error("Unexpected error while create certificate schema validator", iae);
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected String type() {
        return PluginType.CERTIFICATE.name();
    }

}
