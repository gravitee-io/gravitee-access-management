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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.gravitee.am.repository.management.api.search.VerifyAttemptCriteria;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VerifyAttemptRepositoryTest extends AbstractGatewayTest {

    @Autowired
    protected VerifyAttemptRepository repository;

    @Test
    public void shouldCreate() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();

        TestObserver<VerifyAttempt> observer = repository.create(verifyAttempt).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId() != null);
        assertEqualsTo(verifyAttempt, observer);
    }

    @Test
    public void shouldFindById() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();

        TestObserver<VerifyAttempt> observer = repository.findById(createdVerifyAttempt.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));
        assertEqualsTo(verifyAttempt, observer);
    }

    @Test
    public void shouldFindByCriteria() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();
        VerifyAttemptCriteria criteria = new VerifyAttemptCriteria.Builder()
                .userId(createdVerifyAttempt.getUserId())
                .factorId(createdVerifyAttempt.getFactorId())
                .client(createdVerifyAttempt.getClient())
                .build();
        TestObserver<VerifyAttempt> observer = repository.findByCriteria(criteria).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));
        assertEqualsTo(verifyAttempt, observer);
    }

    @Test
    public void shouldNotFindByCriteria_invalidFactorId() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();
        VerifyAttemptCriteria criteria = new VerifyAttemptCriteria.Builder()
                .userId(createdVerifyAttempt.getUserId())
                .factorId("invalid-factor-id")
                .client(createdVerifyAttempt.getClient())
                .build();
        TestObserver<VerifyAttempt> observer = repository.findByCriteria(criteria).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdate() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();

        TestObserver<VerifyAttempt> observer = repository.findById(createdVerifyAttempt.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));

        VerifyAttempt updatableVerifyAttempt = createVerifyAttempt();
        updatableVerifyAttempt.setId(createdVerifyAttempt.getId());
        updatableVerifyAttempt.setAttempts(777);
        updatableVerifyAttempt.setAllowRequest(false);

        TestObserver<VerifyAttempt> updatedObserver = repository.update(updatableVerifyAttempt).test();
        updatedObserver.awaitDone(10, TimeUnit.SECONDS);

        updatedObserver.assertNoErrors();
        updatedObserver.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));
        updatedObserver.assertValue(obj -> obj.getAttempts() == updatableVerifyAttempt.getAttempts());
        updatedObserver.assertValue(obj -> obj.isAllowRequest() == updatableVerifyAttempt.isAllowRequest());
        assertEqualsTo(updatableVerifyAttempt, updatedObserver);
    }

    @Test
    public void shouldDeleteById() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();

        TestObserver<VerifyAttempt> observer = repository.findById(createdVerifyAttempt.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));

        TestObserver<Void> deleteObserver = repository.delete(createdVerifyAttempt.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<VerifyAttempt> afterDeleteObserver = repository.findById(createdVerifyAttempt.getId()).test();
        afterDeleteObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteObserver.assertNoErrors();
        afterDeleteObserver.assertNoValues();

    }

    @Test
    public void shouldDeleteByCriteria() {
        VerifyAttempt verifyAttempt = createVerifyAttempt();
        VerifyAttempt createdVerifyAttempt = repository.create(verifyAttempt).blockingGet();

        TestObserver<VerifyAttempt> observer = repository.findById(createdVerifyAttempt.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdVerifyAttempt.getId()));

        VerifyAttemptCriteria criteria = new VerifyAttemptCriteria.Builder()
                .userId(createdVerifyAttempt.getUserId())
                .factorId(createdVerifyAttempt.getFactorId())
                .client(createdVerifyAttempt.getClient())
                .build();

        TestObserver<Void> deleteObserver = repository.delete(criteria).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<VerifyAttempt> afterDeleteFindObserver = repository.findById(createdVerifyAttempt.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();
    }

    @Test
    public void shouldDeleteByUser() {
        final String userId= "1234-xyz";
        VerifyAttempt verifyAttempt1 = createVerifyAttempt();
        verifyAttempt1.setUserId(userId);
        VerifyAttempt createdVerifyAttempt1 = repository.create(verifyAttempt1).blockingGet();
        TestObserver<VerifyAttempt> observer1 = repository.findById(createdVerifyAttempt1.getId()).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(obj -> obj.getId().equals(createdVerifyAttempt1.getId()));
        observer1.assertValue(obj -> obj.getUserId().equals(userId));

        VerifyAttempt verifyAttempt2 = createVerifyAttempt();
        verifyAttempt2.setUserId(userId);
        VerifyAttempt createdVerifyAttempt2 = repository.create(verifyAttempt2).blockingGet();
        TestObserver<VerifyAttempt> observer2 = repository.findById(createdVerifyAttempt2.getId()).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertValue(obj -> obj.getId().equals(createdVerifyAttempt2.getId()));
        observer2.assertValue(obj -> obj.getUserId().equals(userId));

        TestObserver<Void> deleteObserver = repository.deleteByUser(userId).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<VerifyAttempt> afterDeleteFindObserver = repository.findById(createdVerifyAttempt1.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();

        TestObserver<VerifyAttempt> afterDeleteFindObserver2 = repository.findById(createdVerifyAttempt2.getId()).test();
        afterDeleteFindObserver2.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver2.assertNoErrors();
        afterDeleteFindObserver2.assertNoValues();
    }

    @Test
    public void shouldDeleteByDomain() {
        final String domainId = "1234-xyz";
        VerifyAttempt verifyAttempt1 = createVerifyAttempt();
        verifyAttempt1.setReferenceId(domainId);
        VerifyAttempt createdVerifyAttempt1 = repository.create(verifyAttempt1).blockingGet();
        TestObserver<VerifyAttempt> observer1 = repository.findById(createdVerifyAttempt1.getId()).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(obj -> obj.getId().equals(createdVerifyAttempt1.getId()));
        observer1.assertValue(obj -> obj.getReferenceId().equals(domainId));

        VerifyAttempt verifyAttempt2 = createVerifyAttempt();
        verifyAttempt2.setReferenceId(domainId);
        VerifyAttempt createdVerifyAttempt2 = repository.create(verifyAttempt2).blockingGet();
        TestObserver<VerifyAttempt> observer2 = repository.findById(createdVerifyAttempt2.getId()).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertValue(obj -> obj.getId().equals(createdVerifyAttempt2.getId()));
        observer2.assertValue(obj -> obj.getReferenceId().equals(domainId));

        TestObserver<Void> deleteObserver = repository.deleteByDomain(domainId, ReferenceType.DOMAIN).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<VerifyAttempt> afterDeleteFindObserver = repository.findById(createdVerifyAttempt1.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();

        TestObserver<VerifyAttempt> afterDeleteFindObserver2 = repository.findById(createdVerifyAttempt2.getId()).test();
        afterDeleteFindObserver2.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver2.assertNoErrors();
        afterDeleteFindObserver2.assertNoValues();

    }

    private void assertEqualsTo(VerifyAttempt verifyAttempt, TestObserver<VerifyAttempt> observer) {
        observer.assertValue(observable -> observable.getUserId().equals(verifyAttempt.getUserId()));
        observer.assertValue(observable -> observable.getClient().equals(verifyAttempt.getClient()));
        observer.assertValue(observable -> observable.getFactorId().equals(verifyAttempt.getFactorId()));
        observer.assertValue(observable -> observable.isAllowRequest() == verifyAttempt.isAllowRequest());
    }

    private VerifyAttempt createVerifyAttempt() {
        VerifyAttempt verifyAttempt = new VerifyAttempt();
        final String random = UUID.randomUUID().toString();
        verifyAttempt.setClient("client-id" + random);
        verifyAttempt.setUserId("user-id" + random);
        verifyAttempt.setFactorId("factor-id" + random);
        verifyAttempt.setAllowRequest(true);
        verifyAttempt.setAttempts(2);
        final Date date = new Date();
        verifyAttempt.setCreatedAt(date);
        verifyAttempt.setUpdatedAt(date);
        verifyAttempt.setReferenceId("domain-id" + random);
        verifyAttempt.setReferenceType(ReferenceType.DOMAIN);

        return verifyAttempt;
    }
}
