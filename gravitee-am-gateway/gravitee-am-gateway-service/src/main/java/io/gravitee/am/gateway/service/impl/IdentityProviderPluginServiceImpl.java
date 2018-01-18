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
package io.gravitee.am.gateway.service.impl;

import io.gravitee.am.gateway.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.gateway.service.IdentityProviderPluginService;
import io.gravitee.am.gateway.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.model.plugin.IdentityProviderPlugin;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderPluginServiceImpl implements IdentityProviderPluginService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(IdentityProviderPluginServiceImpl.class);

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Override
    public Set<IdentityProviderPlugin> findAll(Boolean oauth2Provider) {
        try {
            LOGGER.debug("List all identity providers");
            Collection<Plugin> plugins = (oauth2Provider != null && oauth2Provider) ? identityProviderPluginManager.getOAuth2Providers() : identityProviderPluginManager.getAll();
            return plugins
                    .stream()
                    .map(this::convert)
                    .collect(Collectors.toSet());
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to list all identity providers", ex);
            throw new TechnicalManagementException("An error occurs while trying to list all identity providers", ex);
        }
    }

    @Override
    public IdentityProviderPlugin findById(String identityProviderId) {
        LOGGER.debug("Find identity provider by ID: {}", identityProviderId);
        Plugin identityProvider = identityProviderPluginManager.findById(identityProviderId);

        if (identityProvider == null) {
            throw new IdentityProviderNotFoundException(identityProviderId);
        }

        return convert(identityProvider);
    }

    @Override
    public String getSchema(String identityProviderId) {
        try {
            LOGGER.debug("Find identity provider schema by ID: {}", identityProviderId);
            return identityProviderPluginManager.getSchema(identityProviderId);
        } catch (IOException ioex) {
            LOGGER.error("An error occurs while trying to get schema for identity providers {}", identityProviderId, ioex);
            throw new TechnicalManagementException("An error occurs while trying to get schema for identity providers " + identityProviderId, ioex);
        }
    }

    private IdentityProviderPlugin convert(Plugin identityProviderPlugin) {
        IdentityProviderPlugin plugin = new IdentityProviderPlugin();
        plugin.setId(identityProviderPlugin.manifest().id());
        plugin.setName(identityProviderPlugin.manifest().name());
        plugin.setDescription(identityProviderPlugin.manifest().description());
        plugin.setVersion(identityProviderPlugin.manifest().version());
        return plugin;
    }
}
