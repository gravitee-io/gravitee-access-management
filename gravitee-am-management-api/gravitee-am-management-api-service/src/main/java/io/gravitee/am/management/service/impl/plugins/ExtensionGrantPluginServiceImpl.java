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
package io.gravitee.am.management.service.impl.plugins;

import io.gravitee.am.management.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.management.service.ExtensionGrantPluginService;
import io.gravitee.am.service.exception.ExtensionGrantNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.ExtensionGrantPlugin;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ExtensionGrantPluginServiceImpl implements ExtensionGrantPluginService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantPluginServiceImpl.class);

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Override
    public Set<ExtensionGrantPlugin> findAll() {
        try {
            LOGGER.debug("List all extension grants");
            return extensionGrantPluginManager.getAll()
                    .stream()
                    .map(this::convert)
                    .collect(Collectors.toSet());
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to list all extension grants", ex);
            throw new TechnicalManagementException("An error occurs while trying to list all extension grants", ex);
        }
    }

    @Override
    public ExtensionGrantPlugin findById(String extensionGrantPluginId) {
        LOGGER.debug("Find extension grant by ID: {}", extensionGrantPluginId);
        Plugin extensionGrant = extensionGrantPluginManager.findById(extensionGrantPluginId);

        if (extensionGrant == null) {
            throw new ExtensionGrantNotFoundException(extensionGrantPluginId);
        }

        return convert(extensionGrant);
    }

    @Override
    public String getSchema(String tokenGranterPluginId) {
        try {
            LOGGER.debug("Find extension grant schema by ID: {}", tokenGranterPluginId);
            return extensionGrantPluginManager.getSchema(tokenGranterPluginId);
        } catch (IOException ioex) {
            LOGGER.error("An error occurs while trying to get schema for extension grant {}", tokenGranterPluginId, ioex);
            throw new TechnicalManagementException("An error occurs while trying to get schema for extension grant " + tokenGranterPluginId, ioex);
        }
    }

    private ExtensionGrantPlugin convert(Plugin extensionGrantPlugin) {
        ExtensionGrantPlugin plugin = new ExtensionGrantPlugin();
        plugin.setId(extensionGrantPlugin.manifest().id());
        plugin.setName(extensionGrantPlugin.manifest().name());
        plugin.setDescription(extensionGrantPlugin.manifest().description());
        plugin.setVersion(extensionGrantPlugin.manifest().version());
        return plugin;
    }
}
