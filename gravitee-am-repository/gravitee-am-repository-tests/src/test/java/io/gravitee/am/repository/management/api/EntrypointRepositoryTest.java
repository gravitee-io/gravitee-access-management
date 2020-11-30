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

import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EntrypointRepositoryTest extends AbstractManagementTest {
    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private EntrypointRepository entrypointRepository;

    @Test
    public void testFindAll() {

        Entrypoint entrypoint = buildEntrypoint();
        Entrypoint cratedEntrypoint =entrypointRepository.create(entrypoint).blockingGet();

        TestSubscriber<Entrypoint> testObserver1 = entrypointRepository.findAll(ORGANIZATION_ID).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
        assertEquals(entrypoint, cratedEntrypoint.getId(), testObserver1);
    }

    private void assertEquals(Entrypoint entrypoint, String id, BaseTestConsumer<Entrypoint, ? extends BaseTestConsumer> observer) {
        observer.assertValue(e -> e.getId().equals(id));
        observer.assertValue(e -> e.getDescription().equals(entrypoint.getDescription()));
        observer.assertValue(e -> e.getName().equals(entrypoint.getName()));
        observer.assertValue(e -> e.getOrganizationId().equals(entrypoint.getOrganizationId()));
        observer.assertValue(e -> e.getUrl().equals(entrypoint.getUrl()));
        observer.assertValue(e -> e.getTags().size() == entrypoint.getTags().size());
        observer.assertValue(e -> e.getTags().containsAll(entrypoint.getTags()));
    }

    private Entrypoint buildEntrypoint() {
        Entrypoint entrypoint = new Entrypoint();
        String randomString = UUID.randomUUID().toString();
        entrypoint.setName("name"+randomString);
        entrypoint.setDescription("desc"+randomString);
        entrypoint.setDefaultEntrypoint(true);
        entrypoint.setUrl("http://acme.org/"+randomString);
        entrypoint.setCreatedAt(new Date());
        entrypoint.setUpdatedAt(new Date());
        entrypoint.setTags(Arrays.asList("tag1"+randomString, "tag2"+randomString));
        entrypoint.setOrganizationId(ORGANIZATION_ID);
        return entrypoint;
    }

    @Test
    public void testFindById() {

        Entrypoint entrypoint = buildEntrypoint();
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        TestObserver<Entrypoint> testObserver = entrypointRepository.findById(entrypointCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(entrypoint, entrypointCreated.getId(), testObserver);
    }

    @Test
    public void testNotFoundById() {
        entrypointRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Entrypoint entrypoint = buildEntrypoint();
        entrypoint.setId(UUID.randomUUID().toString());
        TestObserver<Entrypoint> testObserver = entrypointRepository.create(entrypoint).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(entrypoint, entrypoint.getId(), testObserver);
    }

    @Test
    public void testUpdate() {

        Entrypoint entrypoint = buildEntrypoint();
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        Entrypoint updatedEntrypoint = buildEntrypoint();
        updatedEntrypoint.setId(entrypointCreated.getId());
        updatedEntrypoint.setName("testUpdatedName");

        TestObserver<Entrypoint> testObserver = entrypointRepository.update(updatedEntrypoint).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(updatedEntrypoint, entrypointCreated.getId(), testObserver);
    }

    @Test
    public void testDelete() {

        Entrypoint entrypoint = buildEntrypoint();
        Entrypoint entrypointCreated = entrypointRepository.create(entrypoint).blockingGet();

        TestObserver<Entrypoint> testObserver = entrypointRepository.findById(entrypointCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(entrypoint, entrypointCreated.getId(), testObserver);

        TestObserver testObserver1 = entrypointRepository.delete(entrypointCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        final TestObserver<Entrypoint> testFind = entrypointRepository.findById(entrypointCreated.getId()).test();
        testFind.awaitTerminalEvent();
        testFind.assertNoValues();
    }

}
