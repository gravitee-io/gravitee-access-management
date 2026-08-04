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

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.plugins.dataplane.core.DataPlaneLoader;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ProvisionedDataPlaneLoaderTest {

    @Mock
    private DataPlaneDefinitionRepository dataPlaneDefinitionRepository;

    private ConfigurableEnvironment environment;
    private List<DataPlaneDescription> loaded;
    private List<DataPlaneDescription> fromConfiguration;

    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
        loaded = new ArrayList<>();
        fromConfiguration = new ArrayList<>();
    }

    private ProvisionedDataPlaneLoader loader() {
        DataPlaneLoader configurationLoader = storage -> fromConfiguration.forEach(storage);
        return new ProvisionedDataPlaneLoader(configurationLoader, dataPlaneDefinitionRepository, environment);
    }

    private static DataPlaneDefinition definition(String id, String type, String configuration) {
        var definition = new DataPlaneDefinition();
        definition.setId(id);
        definition.setName(id + "-name");
        definition.setType(type);
        definition.setGatewayUrl("https://gw.example.com");
        definition.setOrganizationId("DEFAULT");
        definition.setEnvironmentId("DEFAULT");
        definition.setConfiguration(configuration);
        return definition;
    }

    @Test
    void should_emit_the_configured_data_planes_before_the_stored_ones() {
        fromConfiguration.add(new DataPlaneDescription("default", "Default", "mongodb", "dataPlanes[0]", null));
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        loader().load(loaded::add);

        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("default", "dp-1");
    }

    @Test
    void should_describe_a_stored_definition_against_its_own_properties_base() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        loader().load(loaded::add);

        assertThat(loaded).singleElement().isEqualTo(new DataPlaneDescription(
                "dp-1", "dp-1-name", "mongodb", "dataPlanes.provisioned.dp-1", "https://gw.example.com"));
    }

    @Test
    void should_publish_the_stored_configuration_onto_the_environment() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb",
                        "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\",\"sslEnabled\":true,\"maxSize\":50}}")));

        loader().load(loaded::add);

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.uri")).isEqualTo("mongodb://h:27017/db");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.sslEnabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.maxSize", Integer.class)).isEqualTo(50);
    }

    @Test
    void should_index_list_entries_the_way_the_connection_providers_read_them() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb",
                        "{\"mongodb\":{\"dbname\":\"db\",\"servers\":[{\"host\":\"h1\",\"port\":27017},{\"host\":\"h2\",\"port\":27018}]}}")));

        loader().load(loaded::add);

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.servers[0].host")).isEqualTo("h1");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.servers[0].port", Integer.class)).isEqualTo(27017);
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.servers[1].host")).isEqualTo("h2");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.servers[2].host")).isNull();
    }

    @Test
    void should_keep_each_definition_under_its_own_prefix() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(
                definition("dp-1", "mongodb", "{\"mongodb\":{\"dbname\":\"one\",\"host\":\"h1\"}}"),
                definition("dp-2", "jdbc", "{\"jdbc\":{\"database\":\"two\",\"host\":\"h2\"}}")));

        loader().load(loaded::add);

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.dbname")).isEqualTo("one");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-2.jdbc.database")).isEqualTo("two");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.jdbc.database")).isNull();
    }

    @Test
    void should_publish_the_configuration_before_the_description_is_handed_over() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        List<String> resolvedAtRegistration = new ArrayList<>();
        // the registry builds the provider inside this callback, and the provider resolves its own settings
        loader().load(description -> resolvedAtRegistration.add(environment.getProperty(description.propertiesBase() + ".mongodb.uri")));

        assertThat(resolvedAtRegistration).containsExactly("mongodb://h:27017/db");
    }

    @Test
    void should_not_expose_the_configuration_through_the_source_the_node_configuration_endpoint_dumps() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"password\":\"s3cret\"}}")));

        loader().load(loaded::add);

        assertThat(environment.getPropertySources().get("graviteeYamlConfiguration")).isNull();
        assertThat(environment.getPropertySources().get(ProvisionedDataPlaneLoader.PROPERTY_SOURCE_NAME)).isNotNull();
    }

    @Test
    void should_skip_a_definition_whose_configuration_cannot_be_read() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(
                definition("dp-broken", "mongodb", "not json"),
                definition("dp-2", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        loader().load(loaded::add);

        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("dp-2");
    }

    @Test
    void should_skip_a_definition_the_registry_refuses() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(
                definition("dp-rejected", "gone", "{\"gone\":{\"host\":\"h\"}}"),
                definition("dp-2", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        // stands in for the registry failing to build a provider, e.g. the type's plugin is not deployed
        loader().load(description -> {
            if ("gone".equals(description.type())) {
                throw new IllegalStateException("No data plan provider is registered for type gone");
            }
            loaded.add(description);
        });

        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("dp-2");
    }

    @Test
    void should_register_a_definition_provisioned_after_startup() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.load(loaded::add);
        when(dataPlaneDefinitionRepository.findById("dp-1"))
                .thenReturn(Maybe.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        loader.register("dp-1").test().assertComplete();

        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("dp-1");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.uri")).isEqualTo("mongodb://h:27017/db");
    }

    @Test
    void should_not_register_a_definition_the_startup_load_already_picked_up() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));
        var loader = loader();
        loader.load(loaded::add);

        loader.register("dp-1").test().assertComplete();

        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("dp-1");
        verify(dataPlaneDefinitionRepository, never()).findById(any());
    }

    @Test
    void should_ignore_a_registration_that_arrives_before_the_registry_has_started() {
        loader().register("dp-1").test().assertComplete();

        verify(dataPlaneDefinitionRepository, never()).findById(any());
    }

    @Test
    void should_not_fail_the_caller_when_the_definition_cannot_be_read_back() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.load(loaded::add);
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.error(new RuntimeException("connection lost")));

        loader.register("dp-1").test().assertComplete();

        assertThat(loaded).isEmpty();
    }
}
