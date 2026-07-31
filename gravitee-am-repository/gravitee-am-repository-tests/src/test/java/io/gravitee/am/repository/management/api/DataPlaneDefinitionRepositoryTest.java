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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class DataPlaneDefinitionRepositoryTest extends AbstractManagementTest {

    private static final String MONGO_CONFIGURATION = "{\"mongodb\":{\"dbname\":\"gravitee-am-acme\",\"host\":\"mongo\",\"port\":27017}}";

    @Autowired
    private DataPlaneDefinitionRepository dataPlaneDefinitionRepository;

    private DataPlaneDefinition build(String id, String environmentId) {
        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(id);
        definition.setName("Data plane " + id);
        definition.setType("mongodb");
        definition.setGatewayUrl("https://gw-" + id + ".cloud.gravitee.io");
        definition.setOrganizationId("DEFAULT");
        definition.setEnvironmentId(environmentId);
        definition.setConfiguration(MONGO_CONFIGURATION);
        definition.setCreatedAt(new Date());
        definition.setUpdatedAt(new Date());
        return definition;
    }

    @Test
    public void testCreate() {
        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.create(build("dp-create", "env-create")).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> "dp-create".equals(d.getId()));
        obs.assertValue(d -> "env-create".equals(d.getEnvironmentId()));
    }

    @Test
    public void testFindById() {
        dataPlaneDefinitionRepository.create(build("dp-find", "env-find")).blockingGet();

        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.findById("dp-find").test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> "dp-find".equals(d.getId()));
        obs.assertValue(d -> "Data plane dp-find".equals(d.getName()));
        obs.assertValue(d -> "mongodb".equals(d.getType()));
        obs.assertValue(d -> "https://gw-dp-find.cloud.gravitee.io".equals(d.getGatewayUrl()));
        obs.assertValue(d -> "DEFAULT".equals(d.getOrganizationId()));
        obs.assertValue(d -> "env-find".equals(d.getEnvironmentId()));
        obs.assertValue(d -> d.getCreatedAt() != null);
        obs.assertValue(d -> d.getUpdatedAt() != null);
    }

    @Test
    public void testNotFoundById() {
        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.findById("unknown").test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertNoErrors();
        obs.assertNoValues();
    }

    @Test
    public void testConfigurationRoundTripsUnchanged() {
        dataPlaneDefinitionRepository.create(build("dp-config", "env-config")).blockingGet();

        DataPlaneDefinition found = dataPlaneDefinitionRepository.findById("dp-config").blockingGet();

        assertNotNull(found);
        assertEquals(MONGO_CONFIGURATION, found.getConfiguration());
    }

    @Test
    public void testFindByEnvironmentId() {
        dataPlaneDefinitionRepository.create(build("dp-env-1", "env-1")).blockingGet();
        dataPlaneDefinitionRepository.create(build("dp-env-2", "env-2")).blockingGet();

        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.findByEnvironmentId("env-2").test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> "dp-env-2".equals(d.getId()));
    }

    @Test
    public void testFindByUnknownEnvironmentId() {
        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.findByEnvironmentId("env-unknown").test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertNoErrors();
        obs.assertNoValues();
    }

    @Test
    public void testFindAllEmpty() {
        TestObserver<List<DataPlaneDefinition>> obs = dataPlaneDefinitionRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(List::isEmpty);
    }

    @Test
    public void testFindAll() {
        dataPlaneDefinitionRepository.create(build("dp-all-1", "env-all-1")).blockingGet();
        dataPlaneDefinitionRepository.create(build("dp-all-2", "env-all-2")).blockingGet();

        TestObserver<List<DataPlaneDefinition>> obs = dataPlaneDefinitionRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(items -> items.size() == 2);
        obs.assertValue(items -> items.stream().map(DataPlaneDefinition::getId).toList()
                .containsAll(List.of("dp-all-1", "dp-all-2")));
    }

    @Test
    public void testUpdate() {
        dataPlaneDefinitionRepository.create(build("dp-update", "env-update")).blockingGet();

        DataPlaneDefinition updated = build("dp-update", "env-update");
        updated.setName("Renamed");
        updated.setGatewayUrl("https://gw-renamed.cloud.gravitee.io");

        TestObserver<DataPlaneDefinition> obs = dataPlaneDefinitionRepository.update(updated).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(d -> "dp-update".equals(d.getId()));
        obs.assertValue(d -> "Renamed".equals(d.getName()));
        obs.assertValue(d -> "https://gw-renamed.cloud.gravitee.io".equals(d.getGatewayUrl()));
    }

    @Test
    public void testDelete() {
        dataPlaneDefinitionRepository.create(build("dp-delete", "env-delete")).blockingGet();
        assertNotNull(dataPlaneDefinitionRepository.findById("dp-delete").blockingGet());

        TestObserver<Void> obs = dataPlaneDefinitionRepository.delete("dp-delete").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();

        assertNull(dataPlaneDefinitionRepository.findById("dp-delete").blockingGet());
    }
}
