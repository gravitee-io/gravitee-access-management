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
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
    void should_leave_the_environment_alone_until_it_is_loaded() {
        loader();

        assertThat(environment.getPropertySources().get(ProvisionedDataPlaneLoader.PROPERTY_SOURCE_NAME)).isNull();
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
    void should_register_a_provisioned_definition_as_one_that_must_be_verified() {
        var registry = mock(DataPlaneRegistry.class);
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));
        var loader = loader();
        loader.setRegistry(registry);

        loader.load(loaded::add);

        // the consumer is the unchecked path, and a provisioned plane may not take it
        verify(registry).registerProvisioned(argThat(description -> "dp-1".equals(description.id())));
        assertThat(loaded).isEmpty();
    }

    @Test
    void should_release_the_claim_on_a_definition_the_registry_refuses() {
        var registry = mock(DataPlaneRegistry.class);
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-rejected", "gone", "{\"gone\":{\"host\":\"h\"}}")));
        doThrow(new IllegalStateException("No data plan provider is registered for type gone"))
                .when(registry).registerProvisioned(any());
        var loader = loader();
        loader.setRegistry(registry);

        loader.load(loaded::add);

        // the claim must not outlive the failed activation, or the id stays refused for good
        verify(registry).unregister("dp-rejected");
    }

    @Test
    void should_leave_a_configuration_definition_on_the_unchecked_path() {
        var registry = mock(DataPlaneRegistry.class);
        fromConfiguration.add(new DataPlaneDescription("default", "Default", "mongodb", "dataPlanes[0]", null));
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.setRegistry(registry);

        loader.load(loaded::add);

        // the gravitee.yml planes stay exempt: they reach the consumer, never the verified path
        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("default");
        verify(registry, never()).registerProvisioned(any());
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
    void should_retry_an_id_whose_provider_could_not_be_built() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        var refused = new ArrayList<String>();
        // stands in for the registry refusing the first definition, e.g. its store was unreachable
        loader.load(description -> {
            if (refused.isEmpty()) {
                refused.add(description.id());
                throw new IllegalStateException("No data plane provider could be built for id " + description.id());
            }
            loaded.add(description);
        });
        when(dataPlaneDefinitionRepository.findById("dp-1"))
                .thenReturn(Maybe.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}")));

        loader.register("dp-1").test().assertComplete();
        loader.register("dp-1").test().assertComplete();

        assertThat(refused).containsExactly("dp-1");
        assertThat(loaded).extracting(DataPlaneDescription::id).containsExactly("dp-1");
    }

    @Test
    void should_not_leave_stale_properties_behind_when_a_corrected_definition_replaces_a_failed_one() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        var refused = new ArrayList<String>();
        loader.load(description -> {
            if (refused.isEmpty()) {
                refused.add(description.id());
                throw new IllegalStateException("No data plane provider could be built for id " + description.id());
            }
            loaded.add(description);
        });
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(
                Maybe.just(definition("dp-1", "jdbc", "{\"jdbc\":{\"host\":\"h1\",\"collation\":\"latin1\"}}")),
                Maybe.just(definition("dp-1", "jdbc", "{\"jdbc\":{\"host\":\"h2\"}}")));

        loader.register("dp-1").test().assertComplete();
        loader.register("dp-1").test().assertComplete();

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.jdbc.host")).isEqualTo("h2");
        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.jdbc.collation")).isNull();
    }

    @Test
    void should_complete_quietly_when_the_definition_is_missing_at_registration_time() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.load(loaded::add);
        when(dataPlaneDefinitionRepository.findById("dp-1")).thenReturn(Maybe.empty());

        loader.register("dp-1").test().assertComplete();

        assertThat(loaded).isEmpty();
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

    @Test
    void should_drop_the_published_properties_of_a_forgotten_definition() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://gone:27017/db\"}}")));
        var loader = loader();
        loader.load(loaded::add);

        loader.forget("dp-1");

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.uri")).isNull();
    }

    @Test
    void should_leave_other_definitions_alone_when_one_is_forgotten() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(
                definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://one:27017/db\"}}"),
                definition("dp-2", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://two:27017/db\"}}")));
        var loader = loader();
        loader.load(loaded::add);

        loader.forget("dp-1");

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-2.mongodb.uri")).isEqualTo("mongodb://two:27017/db");
    }

    @Test
    void should_let_a_forgotten_id_be_provisioned_again() {
        when(dataPlaneDefinitionRepository.findAll())
                .thenReturn(Flowable.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://old:27017/db\"}}")));
        var loader = loader();
        loader.load(loaded::add);
        loader.forget("dp-1");
        when(dataPlaneDefinitionRepository.findById("dp-1"))
                .thenReturn(Maybe.just(definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://new:27017/db\"}}")));

        loader.register("dp-1").test().assertComplete();

        assertThat(environment.getProperty("dataPlanes.provisioned.dp-1.mongodb.uri")).isEqualTo("mongodb://new:27017/db");
        assertThat(loaded).hasSize(2);
    }

    @Test
    void should_ignore_forgetting_an_unknown_id() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.load(loaded::add);

        loader.forget("never-provisioned");

        assertThat(loaded).isEmpty();
    }

    @Test
    void should_report_it_serves_a_definition_it_has_activated() {
        var definition = definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}");
        definition.setUpdatedAt(new Date(1000L));
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(definition));
        var loader = loader();
        loader.load(loaded::add);

        assertThat(loader.isServing(definition)).isTrue();
    }

    @Test
    void should_not_report_it_serves_a_definition_it_has_never_seen() {
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.empty());
        var loader = loader();
        loader.load(loaded::add);

        var definition = definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}");
        definition.setUpdatedAt(new Date(1000L));

        assertThat(loader.isServing(definition)).isFalse();
    }

    /**
     * A delete and a re-create of one id arrive collapsed as a lone deploy, carrying a definition the
     * node has not seen. Reporting it as served would leave the node on the definition that has gone.
     */
    @Test
    void should_not_report_it_serves_a_definition_carrying_a_later_timestamp() {
        var activated = definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://old:27017/db\"}}");
        activated.setUpdatedAt(new Date(1000L));
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(activated));
        var loader = loader();
        loader.load(loaded::add);

        var recreated = definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://new:27017/db\"}}");
        recreated.setUpdatedAt(new Date(2000L));

        assertThat(loader.isServing(recreated)).isFalse();
    }

    @Test
    void should_not_report_it_serves_a_forgotten_definition() {
        var definition = definition("dp-1", "mongodb", "{\"mongodb\":{\"uri\":\"mongodb://h:27017/db\"}}");
        definition.setUpdatedAt(new Date(1000L));
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(definition));
        var loader = loader();
        loader.load(loaded::add);

        loader.forget("dp-1");

        assertThat(loader.isServing(definition)).isFalse();
    }
}
