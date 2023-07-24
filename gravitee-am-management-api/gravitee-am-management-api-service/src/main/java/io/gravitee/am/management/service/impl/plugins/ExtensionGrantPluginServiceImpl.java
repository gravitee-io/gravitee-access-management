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

import io.gravitee.am.management.service.ExtensionGrantPluginService;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.ExtensionGrantPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ExtensionGrantPluginServiceImpl extends AbstractPluginService implements ExtensionGrantPluginService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantPluginServiceImpl.class);

    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Autowired
    public ExtensionGrantPluginServiceImpl(ExtensionGrantPluginManager extensionGrantPluginManager) {
        super(extensionGrantPluginManager);
        this.extensionGrantPluginManager = extensionGrantPluginManager;
    }

    @Override
    public Single<Set<ExtensionGrantPlugin>> findAll() {
        LOGGER.debug("List all extension grant plugins");
        return Single.create(emitter -> {
            try {
                emitter.onSuccess(extensionGrantPluginManager.findAll(true)
                        .stream()
                        .map(this::convert)
                        .collect(Collectors.toSet()));
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to list all extension grant plugins", ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to list all extension grant plugins", ex));
            }
        });
    }

    @Override
    public Maybe<ExtensionGrantPlugin> findById(String extensionGrantPluginId) {
        LOGGER.debug("Find extension grant plugin by ID: {}", extensionGrantPluginId);
        return Maybe.create(emitter -> {
            try {
                Plugin extensionGrant = extensionGrantPluginManager.findById(extensionGrantPluginId);
                if (extensionGrant != null) {
                    emitter.onSuccess(convert(extensionGrant));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get extension grant plugin : {}", extensionGrantPluginId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get extension grant plugin : " + extensionGrantPluginId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String extensionGrantPluginId) {
        LOGGER.debug("Find extension grant plugin schema by ID: {}", extensionGrantPluginId);
        return Maybe.create(emitter -> {
            try {
                String schema = extensionGrantPluginManager.getSchema(extensionGrantPluginId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for extension grant plugin {}", extensionGrantPluginId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for extension grant plugin " + extensionGrantPluginId, e));
            }
        });
    }

    private ExtensionGrantPlugin convert(Plugin plugin) {
        var extensionGrantPlugin = new ExtensionGrantPlugin();
        extensionGrantPlugin.setId(plugin.manifest().id());
        extensionGrantPlugin.setName(plugin.manifest().name());
        extensionGrantPlugin.setDescription(plugin.manifest().description());
        extensionGrantPlugin.setVersion(plugin.manifest().version());
        extensionGrantPlugin.setDeployed(plugin.deployed());
        extensionGrantPlugin.setFeature(plugin.manifest().feature());
        return extensionGrantPlugin;
    }
}
