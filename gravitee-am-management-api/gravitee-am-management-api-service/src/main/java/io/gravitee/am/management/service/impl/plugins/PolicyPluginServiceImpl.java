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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.PolicyPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PolicyPluginServiceImpl implements PolicyPluginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyPluginServiceImpl.class);

    private final PolicyPluginManager policyPluginManager;
    private final ObjectMapper objectMapper;

    public PolicyPluginServiceImpl(PolicyPluginManager policyPluginManager, ObjectMapper objectMapper) {
        this.policyPluginManager = policyPluginManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Single<List<PolicyPlugin>> findAll(List<String> expand) {
        LOGGER.debug("List all policy plugins");
        return Observable.fromIterable(policyPluginManager.getAll(true))
                .map(policyPlugin -> convert(policyPlugin, expand))
                .toList();
    }

    @Override
    public Maybe<PolicyPlugin> findById(String policyId) {
        LOGGER.debug("Find policy plugin by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                ofNullable(convert(policyPluginManager.get(policyId))).ifPresentOrElse(
                        emitter::onSuccess,
                        emitter::onComplete
                );
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get policy plugin : {}", policyId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get policy plugin : " + policyId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String policyId) {
        LOGGER.debug("Find policy plugin schema by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                final String schema = policyPluginManager.getSchema(policyId);
                ofNullable(removeUnwantedProperties(schema)).ifPresentOrElse(
                        emitter::onSuccess,
                        emitter::onComplete
                );
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for policy plugin {}", policyId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for policy plugin " + policyId, e));
            }
        });
    }

    private String removeUnwantedProperties(String schema) throws JsonProcessingException {
        if (schema == null) {
            return null;
        }
        JsonNode schemaNode = objectMapper.readTree(schema);
        if (schemaNode.has("properties")) {
            ObjectNode properties = (ObjectNode) schemaNode.get("properties");
            properties.remove("scope");
            properties.remove("onResponseScript");
            properties.remove("onRequestContentScript");
            properties.remove("onResponseContentScript");
        }
        return objectMapper.writeValueAsString(schemaNode);
    }

    @Override
    public Maybe<String> getIcon(String policyId) {
        LOGGER.debug("Find policy plugin icon by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                final String icon = policyPluginManager.getIcon(policyId);
                ofNullable(icon).ifPresentOrElse(
                        emitter::onSuccess,
                        emitter::onComplete
                );
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get icon for policy plugin {}", policyId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get icon for policy plugin " + policyId, e));
            }
        });
    }

    @Override
    public Maybe<String> getDocumentation(String policyId) {
        LOGGER.debug("Find policy plugin documentation by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                final String documentation = policyPluginManager.getDocumentation(policyId);
                ofNullable(documentation).ifPresentOrElse(
                        emitter::onSuccess,
                        emitter::onComplete
                );
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get documentation for policy plugin {}", policyId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get documentation for policy plugin " + policyId, e));
            }
        });
    }

    private PolicyPlugin convert(Plugin policyPlugin) {
        return this.convert(policyPlugin, null);
    }

    private PolicyPlugin convert(Plugin plugin, List<String> expand) {
        if (plugin == null) {
            return null;
        }
        var policyPlugin = new PolicyPlugin();
        policyPlugin.setId(plugin.manifest().id());
        policyPlugin.setName(plugin.manifest().name());
        policyPlugin.setDescription(plugin.manifest().description());
        policyPlugin.setVersion(plugin.manifest().version());
        policyPlugin.setDeployed(plugin.deployed());
        if (nonNull(expand) && !expand.isEmpty()) {
            for (String s : expand) {
                switch (s) {
                    case "schema":
                        getSchema(policyPlugin.getId()).subscribe(policyPlugin::setSchema);
                        break;
                    case "icon":
                        getIcon(policyPlugin.getId()).subscribe(policyPlugin::setIcon);
                        break;
                    default:
                        break;
                }
            }
        }
        policyPlugin.setFeature(plugin.manifest().feature());
        return policyPlugin;
    }

    public Completable checkPluginDeployment(String type) {
        if (this.policyPluginManager.get(type) == null || !this.policyPluginManager.get(type).deployed()) {
            LOGGER.debug("Plugin {} not deployed", type);
            return Completable.error(PluginNotDeployedException.forType(type));
        }
        return Completable.complete();
    }
}
