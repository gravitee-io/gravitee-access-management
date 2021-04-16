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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoEntrypointRepositoryTest extends AbstractManagementRepositoryTest {

    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private EntrypointRepository entrypointRepository;

    @Override
    public String collectionName() {
        return "entrypoints";
    }

    @Test
    public void testFindAll() {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setName("testName");
        entrypoint.setDescription("Description");
        entrypoint.setOrganizationId(ORGANIZATION_ID);
        entrypointRepository.create(entrypoint).blockingGet();

        TestSubscriber<Entrypoint> testObserver1 = entrypointRepository.findAll(ORGANIZATION_ID).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
    }

    @Test
    public void testFindById() {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setName("testName");
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        TestObserver<Entrypoint> testObserver = entrypointRepository.findById(entrypointCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() {
        entrypointRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setName("testName");

        TestObserver<Entrypoint> testObserver = entrypointRepository.create(entrypoint).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(entrypointCreated -> entrypointCreated.getName().equals(entrypoint.getName()));
    }

    @Test
    public void testUpdate() {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setName("testName");
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        Entrypoint updatedEntrypoint = new Entrypoint();
        updatedEntrypoint.setId(entrypointCreated.getId());
        updatedEntrypoint.setName("testUpdatedName");

        TestObserver<Entrypoint> testObserver = entrypointRepository.update(updatedEntrypoint).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(t -> t.getName().equals(updatedEntrypoint.getName()));
    }

    @Test
    public void testDelete() {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setName("testName");
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        TestObserver<Entrypoint> testObserver = entrypointRepository.findById(entrypointCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(t -> t.getName().equals(entrypoint.getName()));

        TestObserver testObserver1 = entrypointRepository.delete(entrypointCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        entrypointRepository.findById(entrypointCreated.getId()).test().assertEmpty();
    }
}
