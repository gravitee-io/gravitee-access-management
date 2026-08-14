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
package io.gravitee.am.service.dataplane;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.plugins.dataplane.core.DataPlaneLoader;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.reactivex.rxjava3.core.Completable;
import lombok.CustomLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loads the data planes declared in the gravitee.yml, then the provisioned ones stored in the
 * management repository.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class ProvisionedDataPlaneLoader implements DataPlaneLoader {

    // must not be "graviteeYamlConfiguration": gravitee-node's /_node/configuration endpoint dumps that
    // source key by key, and these definitions carry credentials
    static final String PROPERTY_SOURCE_NAME = "provisionedDataPlanes";

    static final String PROPERTIES_BASE = "dataPlanes.provisioned";

    private final DataPlaneLoader configurationLoader;
    private final DataPlaneDefinitionRepository dataPlaneDefinitionRepository;
    private final ConfigurableEnvironment environment;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    // id -> the version of the definition this node serves, so a deploy can be told from a replay
    private final Map<String, String> registered = new ConcurrentHashMap<>();
    private final AtomicReference<Consumer<DataPlaneDescription>> storageRef = new AtomicReference<>();
    // set after construction: the registry is built from this loader, so it cannot be a constructor argument
    private final AtomicReference<DataPlaneRegistry> registryRef = new AtomicReference<>();

    public void setRegistry(DataPlaneRegistry registry) {
        this.registryRef.set(registry);
    }

    public ProvisionedDataPlaneLoader(DataPlaneLoader configurationLoader,
                                      DataPlaneDefinitionRepository dataPlaneDefinitionRepository,
                                      ConfigurableEnvironment environment) {
        this.configurationLoader = configurationLoader;
        this.dataPlaneDefinitionRepository = dataPlaneDefinitionRepository;
        this.environment = environment;
    }

    @Override
    public void load(Consumer<DataPlaneDescription> storage) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        storageRef.set(storage);
        configurationLoader.load(storage);
        dataPlaneDefinitionRepository.findAll()
                .blockingForEach(definition -> activate(definition, storage));
    }

    public Completable register(String dataPlaneId) {
        var storage = storageRef.get();

        // nothing is lost when the registry has not started yet: load publishes its consumer before
        // reading the repository, so the definition just persisted is picked up by that read
        if (storage == null || registered.containsKey(dataPlaneId)) {
            return Completable.complete();
        }

        return dataPlaneDefinitionRepository.findById(dataPlaneId)
                .doOnSuccess(definition -> activate(definition, storage))
                // fires only when findById completes empty
                .doOnComplete(() -> log.warn("Data plane [{}] was not found when read back after being provisioned and will be unavailable until restart", dataPlaneId))
                .ignoreElement()
                .doOnError(e -> log.error("Data plane [{}] could not be read back after being provisioned and will be unavailable until restart", dataPlaneId, e))
                .onErrorComplete();
    }

    private void activate(DataPlaneDefinition definition, Consumer<DataPlaneDescription> storage) {
        try {
            storage.accept(publish(definition));
            markServing(definition);
            log.info("Data plane [{}] of type [{}] loaded from the management repository", definition.getId(), definition.getType());
            // only the provisioned definitions reach here, which is what keeps the gravitee.yml ones exempt
            var registry = registryRef.get();
            if (registry != null) {
                registry.requireVerification(definition.getId());
            }
        } catch (Exception e) {
            log.error("Data plane [{}] of type [{}] could not be loaded and will be unavailable; domains bound to it cannot be served",
                    definition.getId(), definition.getType(), e);
        }
    }

    public void forget(String dataPlaneId) {
        registered.remove(dataPlaneId);
        properties.keySet().removeIf(key -> key.startsWith(PROPERTIES_BASE + "." + dataPlaneId + "."));
    }

    /**
     * Whether this node already serves this exact definition. The node handling a provisioning request
     * registers it directly and then reads its own event back, since the sync poll is not filtered by
     * origin. Rebuilding on that event would stop a provider that is already current and close its
     * connection pool under whatever is using it.
     */
    public boolean isServing(DataPlaneDefinition definition) {
        return versionOf(definition).equals(registered.get(definition.getId()));
    }

    public void markServing(DataPlaneDefinition definition) {
        registered.put(definition.getId(), versionOf(definition));
    }

    /**
     * A delete and a re-create of one id can reach a node collapsed into a single deploy, and that pair
     * moves the timestamp, so the replacement is still rebuilt.
     */
    private static String versionOf(DataPlaneDefinition definition) {
        var updatedAt = definition.getUpdatedAt();
        return updatedAt == null ? "" : Long.toString(updatedAt.getTime());
    }

    public DataPlaneDescription publish(DataPlaneDefinition definition) throws Exception {
        var propertiesBase = PROPERTIES_BASE + "." + definition.getId();

        properties.keySet().removeIf(key -> key.startsWith(propertiesBase + "."));
        flatten(propertiesBase, objectMapper.readTree(definition.getConfiguration()), properties);

        return new DataPlaneDescription(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                propertiesBase,
                definition.getGatewayUrl());
    }

    private static void flatten(String prefix, JsonNode node, Map<String, Object> properties) {
        if (node.isObject()) {
            node.properties().forEach(property -> flatten(prefix + "." + property.getKey(), property.getValue(), properties));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), properties);
            }
        } else if (!node.isNull()) {
            properties.put(prefix, node.asText());
        }
    }
}
