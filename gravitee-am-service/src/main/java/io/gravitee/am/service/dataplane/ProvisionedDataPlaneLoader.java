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
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.reactivex.rxjava3.core.Completable;
import lombok.CustomLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loads the data planes declared in the gravitee.yml, then the ones provisioned through the internal
 * API (AM-7259) and stored in the management repository. {@link #register(String)} adds a definition
 * provisioned since startup, so a new data plane can be served without restarting the node.
 *
 * A provider never receives its configuration: {@link DataPlaneDescription#propertiesBase()} hands it a
 * property prefix and the plugin resolves the settings out of the Spring environment itself. A stored
 * definition only holds a JSON blob, so its settings are flattened onto dotted keys under
 * {@code dataPlanes.provisioned.<id>} and published as a property source before the description is
 * handed over. Every existing read site — both plugins, both connection providers, liquibase — resolves
 * through that same environment, so none of them need to know where a definition came from.
 *
 * Reads {@link DataPlaneDefinitionRepository} rather than {@code DataPlaneDefinitionService} on purpose:
 * the service deliberately has no route out for the raw configuration.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class ProvisionedDataPlaneLoader implements DataPlaneLoader {

    /**
     * Must not be {@code graviteeYamlConfiguration}: gravitee-node's {@code /_node/configuration}
     * endpoint enumerates that one source key by key, and these definitions carry credentials.
     */
    static final String PROPERTY_SOURCE_NAME = "provisionedDataPlanes";

    static final String PROPERTIES_BASE = "dataPlanes.provisioned";

    private final DataPlaneLoader configurationLoader;
    private final DataPlaneDefinitionRepository dataPlaneDefinitionRepository;
    // a parse failure is logged, and Jackson quotes the source it choked on unless told not to
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private volatile Consumer<DataPlaneDescription> storage;

    public ProvisionedDataPlaneLoader(DataPlaneLoader configurationLoader,
                                      DataPlaneDefinitionRepository dataPlaneDefinitionRepository,
                                      ConfigurableEnvironment environment) {
        this.configurationLoader = configurationLoader;
        this.dataPlaneDefinitionRepository = dataPlaneDefinitionRepository;
        // the source stays backed by the live map, so definitions published later are visible too
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public void load(Consumer<DataPlaneDescription> storage) {
        // kept so a definition provisioned later reaches the same registry this one is filling
        this.storage = storage;
        configurationLoader.load(storage);
        dataPlaneDefinitionRepository.findAll()
                .blockingForEach(definition -> load(definition, storage));
    }

    /**
     * Registers a definition provisioned since startup so domains bound to it can be served straight away,
     * rather than from the next restart.
     *
     * Best effort on purpose: the definition is persisted before this runs, so failing here only costs the
     * node the live registration — the startup load will pick it up next time — and it must not turn a
     * successful provisioning call into an error.
     */
    public Completable register(String dataPlaneId) {
        var storage = this.storage;
        // nothing registered yet means no registry to add to; its own load() will read this definition
        if (storage == null || !registered.add(dataPlaneId)) {
            return Completable.complete();
        }
        return dataPlaneDefinitionRepository.findById(dataPlaneId)
                .doOnSuccess(definition -> load(definition, storage))
                .ignoreElement()
                .doOnError(e -> log.error("Data plane [{}] could not be read back after being provisioned and will be unavailable until restart", dataPlaneId, e))
                .onErrorComplete();
    }

    /**
     * A stored definition outlives the deployment that created it, so by the time it is read its type's
     * plugin may be gone, its id may collide with a gravitee.yml one, or its configuration may no longer
     * parse. None of that is worth refusing to start the node over: skip the definition and leave the
     * rest of the installation running.
     */
    private void load(DataPlaneDefinition definition, Consumer<DataPlaneDescription> storage) {
        try {
            storage.accept(publish(definition));
            registered.add(definition.getId());
            log.info("Data plane [{}] of type [{}] loaded from the management repository", definition.getId(), definition.getType());
        } catch (Exception e) {
            log.error("Data plane [{}] of type [{}] could not be loaded and will be unavailable; domains bound to it cannot be served",
                    definition.getId(), definition.getType(), e);
        }
    }

    private DataPlaneDescription publish(DataPlaneDefinition definition) throws Exception {
        var propertiesBase = PROPERTIES_BASE + "." + definition.getId();
        flatten(propertiesBase, objectMapper.readTree(definition.getConfiguration()), properties);
        return new DataPlaneDescription(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                propertiesBase,
                definition.getGatewayUrl());
    }

    /**
     * Mirrors how the gravitee.yml maps onto the environment: nested objects become dotted keys and
     * list entries keep their index, so {@code {"mongodb":{"servers":[{"host":"h"}]}}} publishes
     * {@code <base>.mongodb.servers[0].host}, which is the key {@code MongoFactoryImpl} looks for.
     */
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
