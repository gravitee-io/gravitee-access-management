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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoDomainRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private DomainRepository domainRepository;

    @Override
    public String collectionName() {
        return "domains";
    }

    @Test
    public void testFindAll() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        domainRepository.create(domain).blockingGet();

        // fetch domains
        TestObserver<Set<Domain>> testObserver1 = domainRepository.findAll().test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void testFindAllByEnvironment() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId("environment#1");
        domainRepository.create(domain).blockingGet();

        // create domain on different environment.
        Domain otherDomain = new Domain();
        otherDomain.setName("testName");
        otherDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        otherDomain.setReferenceId("environment#2");
        domainRepository.create(otherDomain).blockingGet();

        // fetch domains
        TestSubscriber<Domain> testObserver1 = domainRepository.findAllByEnvironment("environment#1").test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
    }

    @Test
    public void testFindInIds() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domains
        TestObserver<Set<Domain>> testObserver1 = domainRepository.findByIdIn(Collections.singleton(domainCreated.getId())).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        domainRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Domain domain = new Domain();
        domain.setName("testName");

        TestObserver<Domain> testObserver = domainRepository.create(domain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domainCreated -> domainCreated.getName().equals(domain.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // update domain
        Domain updatedDomain = new Domain();
        updatedDomain.setId(domainCreated.getId());
        updatedDomain.setName("testUpdatedName");

        TestObserver<Domain> testObserver = domainRepository.update(updatedDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(updatedDomain.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create domain
        Domain domain = new Domain();
        domain.setName("testName");
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(domain.getName()));

        // delete domain
        TestObserver testObserver1 = domainRepository.delete(domainCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch domain
        domainRepository.findById(domainCreated.getId()).test().assertEmpty();
    }
}
