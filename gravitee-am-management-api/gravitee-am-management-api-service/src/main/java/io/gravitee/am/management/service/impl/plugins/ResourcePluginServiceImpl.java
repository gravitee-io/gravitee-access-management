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

import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.ResourcePlugin;
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
public class ResourcePluginServiceImpl extends AbstractPluginService implements ResourcePluginService {

    private static final String MANIFEST_KEY_CATEGORIES_SEPARATOR = ",";
    private final Logger LOGGER = LoggerFactory.getLogger(ResourcePluginServiceImpl.class);

    private ResourcePluginManager resourcePluginManager;

    @Autowired
    public ResourcePluginServiceImpl(ResourcePluginManager resourcePluginManager) {
        super(resourcePluginManager);
        this.resourcePluginManager = resourcePluginManager;
    }

    @Override
    public Single<List<ResourcePlugin>> findAll(List<String> expand) {
        LOGGER.debug("List all resource plugins");
        return Observable.fromIterable(resourcePluginManager.findAll(true))
                .map(plugin -> convert(plugin, expand))
                .toList();
    }

    @Override
    public Maybe<ResourcePlugin> findById(String resourceId) {
        LOGGER.debug("Find resource plugin by ID: {}", resourceId);
        return Maybe.create(emitter -> {
            try {
                Plugin resource = resourcePluginManager.findById(resourceId);
                if (resource != null) {
                    emitter.onSuccess(convert(resource));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get resource plugin : {}", resourceId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get resource plugin : " + resourceId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String resourceId) {
        LOGGER.debug("Find resource plugin schema by ID: {}", resourceId);
        return Maybe.create(emitter -> {
            try {
                String schema = resourcePluginManager.getSchema(resourceId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for resource plugin {}", resourceId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for resource plugin " + resourceId, e));
            }
        });
    }

    @Override
    public Maybe<String> getIcon(String resourceId) {
        LOGGER.debug("Find resource plugin icon by ID: {}", resourceId);
        return Maybe.create(emitter -> {
            try {
                String icon = resourcePluginManager.getIcon(resourceId);
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

    private ResourcePlugin convert(Plugin resourcePlugin) {
        return convert(resourcePlugin, null);
    }

    private ResourcePlugin convert(Plugin plugin, List<String> expand) {
        ResourcePlugin resourcePlugin = new ResourcePlugin();
        resourcePlugin.setId(plugin.manifest().id());
        resourcePlugin.setName(plugin.manifest().name());
        resourcePlugin.setDescription(plugin.manifest().description());
        resourcePlugin.setVersion(plugin.manifest().version());
        resourcePlugin.setDeployed(plugin.deployed());
        String tags = plugin.manifest().properties().get(MANIFEST_KEY_CATEGORIES);
        if (tags != null) {
            resourcePlugin.setCategories(tags.split(MANIFEST_KEY_CATEGORIES_SEPARATOR));
        } else {
            resourcePlugin.setCategories(new String[0]);
        }
        if (expand != null) {
            if (expand.contains(ResourcePluginService.EXPAND_ICON)) {
                this.getIcon(resourcePlugin.getId()).subscribe(resourcePlugin::setIcon);
            }
        }
        resourcePlugin.setFeature(plugin.manifest().feature());
        return resourcePlugin;
    }
}
