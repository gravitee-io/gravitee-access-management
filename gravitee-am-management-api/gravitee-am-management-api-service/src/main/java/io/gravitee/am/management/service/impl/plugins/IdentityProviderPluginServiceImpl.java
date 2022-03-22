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
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    public Single<List<IdentityProviderPlugin>> findAll(List<String> expand) {
        return this.findAll(false, null);
    }

    @Override
    public Single<List<IdentityProviderPlugin>> findAll(Boolean external) {
        return this.findAll(external, null);
    }

    @Override
    public Single<List<IdentityProviderPlugin>> findAll(Boolean external, List<String> expand) {
        LOGGER.debug("List all identity provider plugins");
        return Observable.fromIterable(identityProviderPluginManager.getAllEntries().entrySet())
            .filter(entry -> (external != null && external) ? entry.getKey().external() : !entry.getKey().external())
            .map(entry -> convert(entry.getValue(), expand))
            .toList()
            .onErrorResumeNext(ex -> {
                LOGGER.error("An error occurs while trying to list all identity provider plugins", ex);
                return Single.error(new TechnicalManagementException("An error occurs while trying to list all identity provider plugins", ex));
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

    @Override
    public Maybe<String> getIcon(String identityProviderId) {
        LOGGER.debug("Find identity provider plugin schema by ID: {}", identityProviderId);
        return Maybe.create(emitter -> {
            try {
                String icon = identityProviderPluginManager.getIcon(identityProviderId);
                if (icon != null) {
                    emitter.onSuccess(icon);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get icon for identity provider plugin {}", identityProviderId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get icon for identity provider plugin " + identityProviderId, e));
            }
        });
    }

    private IdentityProviderPlugin convert(Plugin identityProviderPlugin) {
        return this.convert(identityProviderPlugin, null);
    }

    private IdentityProviderPlugin convert(Plugin identityProviderPlugin, List<String> expand) {
        IdentityProviderPlugin plugin = new IdentityProviderPlugin();
        plugin.setId(identityProviderPlugin.manifest().id());
        plugin.setName(identityProviderPlugin.manifest().name());

        plugin.setDescription(identityProviderPlugin.manifest().description());
        plugin.setVersion(identityProviderPlugin.manifest().version());
        if (expand != null) {
            if (expand.contains(IdentityProviderPluginService.EXPAND_ICON)) {
                this.getIcon(plugin.getId()).subscribe(plugin::setIcon);
            }
            if (expand.contains(IdentityProviderPluginService.EXPAND_DISPLAY_NAME)) {
                plugin.setDisplayName(identityProviderPlugin.manifest().properties().get(IdentityProviderPluginService.EXPAND_DISPLAY_NAME));
            }
            if (expand.contains(IdentityProviderPluginService.EXPAND_LABELS)) {
                String tags = identityProviderPlugin.manifest().properties().get(IdentityProviderPluginService.EXPAND_LABELS);
                if (tags != null) {
                    plugin.setLabels(tags.split(","));
                }
            }
        }
        return plugin;
    }
}
