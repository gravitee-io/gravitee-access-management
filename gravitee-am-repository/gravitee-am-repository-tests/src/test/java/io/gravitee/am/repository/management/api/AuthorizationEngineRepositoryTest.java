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

import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEngineRepositoryTest extends AbstractManagementTest {

    @Autowired
    private AuthorizationEngineRepository authorizationEngineRepository;

    @Test
    public void testFindAll() {
        AuthorizationEngine engine1 = buildAuthorizationEngine();
        engine1.setReferenceId("domain1");
        authorizationEngineRepository.create(engine1).blockingGet();

        AuthorizationEngine engine2 = buildAuthorizationEngine();
        engine2.setReferenceId("domain2");
        authorizationEngineRepository.create(engine2).blockingGet();

        TestSubscriber<AuthorizationEngine> testSubscriber = authorizationEngineRepository.findAll().test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
    }

    @Test
    public void testFindByDomain() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        engine.setReferenceId("DomainTestFindByDomain");
        authorizationEngineRepository.create(engine).blockingGet();

        TestSubscriber<AuthorizationEngine> testSubscriber = authorizationEngineRepository.findByDomain("DomainTestFindByDomain").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindByDomainAndId() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        engine.setReferenceId("DomainTestFindByDomainAndId");
        AuthorizationEngine created = authorizationEngineRepository.create(engine).blockingGet();

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.findByDomainAndId("DomainTestFindByDomainAndId", created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getId().equals(created.getId()));
        testObserver.assertValue(e -> e.getReferenceId().equals("DomainTestFindByDomainAndId"));
    }

    @Test
    public void testFindByDomainAndType() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        engine.setReferenceId("DomainTestFindByDomainAndType");
        engine.setType("openfga");
        authorizationEngineRepository.create(engine).blockingGet();

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.findByDomainAndType("DomainTestFindByDomainAndType", "openfga").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals("openfga"));
        testObserver.assertValue(e -> e.getReferenceId().equals("DomainTestFindByDomainAndType"));
    }

    @Test
    public void testFindByDomainAndType_NotFound() {
        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.findByDomainAndType("unknownDomain", "unknownType").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void testFindById() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        engine.setName("testFindById");
        AuthorizationEngine created = authorizationEngineRepository.create(engine).blockingGet();

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.findById(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals("testFindById"));
        testObserver.assertValue(e -> e.getId().equals(created.getId()));
        testObserver.assertValue(e -> e.getConfiguration().equals(engine.getConfiguration()));
        testObserver.assertValue(e -> e.getReferenceId().equals(engine.getReferenceId()));
        testObserver.assertValue(e -> e.getType().equals(engine.getType()));
    }

    @Test
    public void testNotFoundById() {
        TestObserver<AuthorizationEngine> observer = authorizationEngineRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        AuthorizationEngine engine = buildAuthorizationEngine();

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.create(engine).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(created -> created.getName().equals(engine.getName()));
    }

    @Test
    public void testUpdate() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        AuthorizationEngine created = authorizationEngineRepository.create(engine).blockingGet();

        AuthorizationEngine updated = new AuthorizationEngine(created);
        updated.setName("testUpdatedName");
        updated.setConfiguration("{\"updatedKey\":\"updatedValue\"}");

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.update(updated).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals("testUpdatedName"));
        testObserver.assertValue(e -> e.getConfiguration().equals("{\"updatedKey\":\"updatedValue\"}"));
    }

    @Test
    public void testDelete() {
        AuthorizationEngine engine = buildAuthorizationEngine();
        AuthorizationEngine created = authorizationEngineRepository.create(engine).blockingGet();

        TestObserver<AuthorizationEngine> testObserver = authorizationEngineRepository.findById(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(created.getName()));

        TestObserver<Void> testObserver1 = authorizationEngineRepository.delete(created.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver = authorizationEngineRepository.findById(created.getId()).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    private AuthorizationEngine buildAuthorizationEngine() {
        AuthorizationEngine engine = new AuthorizationEngine();
        engine.setName("testName");
        engine.setType("testType");
        engine.setConfiguration("{\"key\":\"value\"}");
        engine.setReferenceType(ReferenceType.DOMAIN);
        engine.setReferenceId("testDomain");
        engine.setCreatedAt(new Date());
        engine.setUpdatedAt(new Date());
        return engine;
    }
}
