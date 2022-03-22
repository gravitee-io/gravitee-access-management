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

import io.gravitee.am.botdetection.api.BotDetection;
import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.BotDetectionPlugin;
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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class BotDetectionPluginServiceImpl implements BotDetectionPluginService {

    private final Logger LOGGER = LoggerFactory.getLogger(BotDetectionPluginServiceImpl.class);

    private final AmPluginManager<BotDetection> pluginManager;

    @Autowired
    public BotDetectionPluginServiceImpl(AmPluginManager<BotDetection> pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public Single<List<BotDetectionPlugin>> findAll() {
        LOGGER.debug("List all bot detection plugins");
        return Observable.fromIterable(pluginManager.getAll())
                .map(this::convert)
                .toList();
    }

    @Override
    public Maybe<BotDetectionPlugin> findById(String pluginId) {
        LOGGER.debug("Find bot detection plugin by ID: {}", pluginId);
        return Maybe.create(emitter -> {
            try {
                Plugin resource = pluginManager.findById(pluginId);
                if (resource != null) {
                    emitter.onSuccess(convert(resource));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get bot detection plugin : {}", pluginId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get bot detection plugin : " + pluginId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String pluginId) {
        LOGGER.debug("Find bot detection plugin schema by ID: {}", pluginId);
        return Maybe.create(emitter -> {
            try {
                String schema = pluginManager.getSchema(pluginId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for bot detection plugin {}", pluginId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for bot detection plugin " + pluginId, e));
            }
        });
    }

    private BotDetectionPlugin convert(Plugin botDetectionPlugin) {
        BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId(botDetectionPlugin.manifest().id());
        plugin.setName(botDetectionPlugin.manifest().name());
        plugin.setDescription(botDetectionPlugin.manifest().description());
        plugin.setVersion(botDetectionPlugin.manifest().version());
        plugin.setCategory(botDetectionPlugin.manifest().category());
        return plugin;
    }
}