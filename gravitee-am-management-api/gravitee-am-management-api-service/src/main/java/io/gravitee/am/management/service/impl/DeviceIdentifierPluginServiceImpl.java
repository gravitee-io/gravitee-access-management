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

package io.gravitee.am.management.service.impl;

import io.gravitee.am.management.service.DeviceIdentifierPluginService;
import io.gravitee.am.management.service.impl.plugins.AbstractPluginService;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.DeviceIdentifierPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DeviceIdentifierPluginServiceImpl extends AbstractPluginService implements DeviceIdentifierPluginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceIdentifierPluginServiceImpl.class);

    private DeviceIdentifierPluginManager deviceIdentifierPluginManager;

    @Autowired
    public DeviceIdentifierPluginServiceImpl(DeviceIdentifierPluginManager deviceIdentifierPluginManager) {
        super(deviceIdentifierPluginManager);
        this.deviceIdentifierPluginManager = deviceIdentifierPluginManager;
    }

    @Override
    public Single<List<DeviceIdentifierPlugin>> findAll() {
        LOGGER.debug("List all Device Identifier plugins");
        return Observable.fromIterable(deviceIdentifierPluginManager.findAll(true))
                .map(this::convert)
                .toList();
    }

    @Override
    public Maybe<DeviceIdentifierPlugin> findById(String deviceIdentifierId) {
        LOGGER.debug("Find device identifier plugin by ID: {}", deviceIdentifierId);
        return Maybe.create(emitter -> {
            try {
                var deviceIdentifierPlugin = deviceIdentifierPluginManager.findById(deviceIdentifierId);
                Optional.ofNullable(deviceIdentifierPlugin).ifPresentOrElse(
                        plugin -> emitter.onSuccess(convert(plugin)),
                        emitter::onComplete
                );
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get device identifier plugin : {}", deviceIdentifierId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get factor plugin : " + deviceIdentifierId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String factorId) {
        LOGGER.debug("Find device identifier plugin schema by ID: {}", factorId);
        return Maybe.create(emitter -> {
            try {
                var schema = deviceIdentifierPluginManager.getSchema(factorId);
                Optional.ofNullable(schema).ifPresentOrElse(
                        emitter::onSuccess,
                        emitter::onComplete
                );
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for device identifier plugin {}", factorId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for device identifier plugin " + factorId, e));
            }
        });
    }


    private DeviceIdentifierPlugin convert(Plugin plugin) {
        var deviceIdentifierPlugin = new DeviceIdentifierPlugin();
        deviceIdentifierPlugin.setId(plugin.manifest().id());
        deviceIdentifierPlugin.setName(plugin.manifest().name());
        deviceIdentifierPlugin.setDescription(plugin.manifest().description());
        deviceIdentifierPlugin.setVersion(plugin.manifest().version());
        deviceIdentifierPlugin.setCategory(plugin.manifest().category());
        deviceIdentifierPlugin.setDeployed(plugin.deployed());
        deviceIdentifierPlugin.setFeature(plugin.manifest().feature());
        return deviceIdentifierPlugin;
    }

}
