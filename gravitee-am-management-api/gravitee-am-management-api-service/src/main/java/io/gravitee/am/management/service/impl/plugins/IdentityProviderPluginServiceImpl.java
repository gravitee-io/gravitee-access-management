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

import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.IdentityProviderPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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
    public Single<Set<IdentityProviderPlugin>> findAll(Boolean oauth2Provider) {
        LOGGER.debug("List all identity provider plugins");
        return Single.create(emitter -> {
            try {
                Collection<Plugin> plugins = (oauth2Provider != null && oauth2Provider) ? identityProviderPluginManager.getOAuth2Providers() : identityProviderPluginManager.getAll();
                emitter.onSuccess(plugins
                        .stream()
                        .map(this::convert)
                        .collect(Collectors.toSet()));
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to list all identity provider plugins", ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to list all identity provider plugins", ex));
            }
        });

    }

    @Override
    public Maybe<IdentityProviderPlugin> findById(String identityProviderId) {
        LOGGER.debug("Find identity provider plugin by ID: {}", identityProviderId);
        return Maybe.create(emitter -> {
            try {
                Plugin identityProvider = identityProviderPluginManager.findById(identityProviderId);
                if (identityProvider != null) {
                    emitter.onSuccess(convert(identityProvider));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get identity provider plugin : {}", identityProviderId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get identity provider plugin : " + identityProviderId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String identityProviderId) {
        LOGGER.debug("Find identity provider plugin schema by ID: {}", identityProviderId);
        return Maybe.create(emitter -> {
            try {
                String schema = identityProviderPluginManager.getSchema(identityProviderId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for identity provider plugin {}", identityProviderId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for identity provider plugin " + identityProviderId, e));
            }
        });
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
