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

import io.gravitee.am.management.service.AuthenticationDeviceNotifierPluginService;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.AuthenticationDeviceNotifierPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuthenticationDeviceNotifierPluginServiceImpl extends AbstractPluginService implements AuthenticationDeviceNotifierPluginService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthenticationDeviceNotifierPluginServiceImpl.class);

    private AuthenticationDeviceNotifierPluginManager pluginManager;

    @Autowired
    public AuthenticationDeviceNotifierPluginServiceImpl(AuthenticationDeviceNotifierPluginManager pluginManager) {
        super(pluginManager);
        this.pluginManager = pluginManager;
    }

    @Override
    public Single<List<AuthenticationDeviceNotifierPlugin>> findAll(List<String> expand) {
        LOGGER.debug("List all authentication device notifier plugins");
        return Observable.fromIterable(pluginManager.findAll(true))
                .map(plugin -> convert(plugin, expand))
                .toList();
    }

    @Override
    public Maybe<AuthenticationDeviceNotifierPlugin> findById(String pluginId) {
        LOGGER.debug("Find authentication device notifier plugin by ID: {}", pluginId);
        return Maybe.create(emitter -> {
            try {
                Plugin resource = pluginManager.findById(pluginId);
                if (resource != null) {
                    emitter.onSuccess(convert(resource));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get authentication device notifier plugin : {}", pluginId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get authentication device notifier plugin : " + pluginId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String pluginId) {
        LOGGER.debug("Find authentication device notifier plugin schema by ID: {}", pluginId);
        return Maybe.create(emitter -> {
            try {
                String schema = pluginManager.getSchema(pluginId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for authentication device notifier plugin {}", pluginId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for authentication device notifier plugin " + pluginId, e));
            }
        });
    }

    @Override
    public Maybe<String> getIcon(String resourceId) {
        LOGGER.debug("Find resource plugin icon by ID: {}", resourceId);
        return Maybe.create(emitter -> {
            try {
                String icon = pluginManager.getIcon(resourceId);
                if (icon != null) {
                    emitter.onSuccess(icon);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error has occurred when trying to get icon for resource plugin {}", resourceId, e);
                emitter.onError(new TechnicalManagementException("An error has occurred when trying to get icon for resource plugin " + resourceId, e));
            }
        });
    }

    private AuthenticationDeviceNotifierPlugin convert(Plugin authDeviceNotifierPlugin) {
        return convert(authDeviceNotifierPlugin, null);
    }

    private AuthenticationDeviceNotifierPlugin convert(Plugin plugin, List<String> expand) {
        var authenticationDeviceNotifierPlugin = new AuthenticationDeviceNotifierPlugin();
        authenticationDeviceNotifierPlugin.setId(plugin.manifest().id());
        authenticationDeviceNotifierPlugin.setName(plugin.manifest().name());
        authenticationDeviceNotifierPlugin.setDescription(plugin.manifest().description());
        authenticationDeviceNotifierPlugin.setVersion(plugin.manifest().version());
        authenticationDeviceNotifierPlugin.setDeployed(plugin.deployed());
        if (expand != null) {
            if (expand.contains(AuthenticationDeviceNotifierPluginService.EXPAND_ICON)) {
                this.getIcon(authenticationDeviceNotifierPlugin.getId()).subscribe(authenticationDeviceNotifierPlugin::setIcon);
            }
        }
        authenticationDeviceNotifierPlugin.setFeature(plugin.manifest().feature());
        return authenticationDeviceNotifierPlugin;
    }
}
