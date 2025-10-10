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

import io.gravitee.am.management.service.AuthorizationEnginePluginService;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.AuthorizationEnginePlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationEnginePluginServiceImpl extends AbstractPluginService implements AuthorizationEnginePluginService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationEnginePluginServiceImpl.class);

    private final AuthorizationEnginePluginManager authorizationEnginePluginManager;

    public AuthorizationEnginePluginServiceImpl(AuthorizationEnginePluginManager authorizationEnginePluginManager) {
        super(authorizationEnginePluginManager);
        this.authorizationEnginePluginManager = authorizationEnginePluginManager;
    }

    @Override
    public Single<List<AuthorizationEnginePlugin>> findAll(List<String> expand) {
        LOGGER.debug("List all authorization engine plugins");
        return Observable.fromIterable(authorizationEnginePluginManager.findAll(true))
                .flatMapSingle(plugin -> convert(plugin, expand))
                .toList();
    }

    @Override
    public Maybe<AuthorizationEnginePlugin> findById(String authorizationEngineId) {
        LOGGER.debug("Find authorization engine plugin by ID: {}", authorizationEngineId);
        return Maybe.defer(() -> {
            try {
                Plugin plugin = authorizationEnginePluginManager.findById(authorizationEngineId);
                if (plugin == null) {
                    return Maybe.empty();
                }
                return convert(plugin, null).toMaybe();
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get authorization engine plugin: {}", authorizationEngineId, ex);
                return Maybe.error(new TechnicalManagementException(
                        "An error occurs while trying to get authorization engine plugin: " + authorizationEngineId, ex
                ));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String authorizationEngineId) {
        LOGGER.debug("Find authorization engine plugin schema by ID: {}", authorizationEngineId);
        return Maybe.create(emitter -> {
            try {
                String schema = authorizationEnginePluginManager.getSchema(authorizationEngineId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for authorization engine plugin {}", authorizationEngineId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for authorization engine plugin " + authorizationEngineId, e));
            }
        });
    }

    @Override
    public Maybe<String> getIcon(String authorizationEngineId) {
        LOGGER.debug("Find authorization engine plugin icon by ID: {}", authorizationEngineId);
        return Maybe.create(emitter -> {
            try {
                String icon = authorizationEnginePluginManager.getIcon(authorizationEngineId, true);
                if (icon != null) {
                    emitter.onSuccess(icon);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get icon for authorization engine plugin {}", authorizationEngineId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get icon for authorization engine plugin " + authorizationEngineId, e));
            }
        });
    }

    private Single<AuthorizationEnginePlugin> convert(Plugin plugin, List<String> expand) {
        var authorizationEnginePlugin = new AuthorizationEnginePlugin();
        authorizationEnginePlugin.setId(plugin.manifest().id());
        authorizationEnginePlugin.setName(plugin.manifest().name());
        authorizationEnginePlugin.setDescription(plugin.manifest().description());
        authorizationEnginePlugin.setVersion(plugin.manifest().version());
        authorizationEnginePlugin.setDeployed(plugin.deployed());
        authorizationEnginePlugin.setFeature(plugin.manifest().feature());
        if (expand != null && expand.contains(AuthorizationEnginePluginService.EXPAND_ICON)) {
            return getIcon(plugin.manifest().id())
                    .map(icon -> {
                        authorizationEnginePlugin.setIcon(icon);
                        return authorizationEnginePlugin;
                    })
                    .toSingle();
        }
        return Single.just(authorizationEnginePlugin);
    }
}
