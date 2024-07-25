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

import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.gravitee.am.repository.management.api.search.RateLimitCriteria;
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
public class RateLimitRepositoryTest extends AbstractGatewayTest {

    @Autowired
    protected RateLimitRepository repository;

    @Test
    public void shouldCreate() {
        RateLimit rateLimit = createRateLimit();

        TestObserver<RateLimit> observer = repository.create(rateLimit).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId() != null);
        assertEqualsTo(rateLimit, observer);
    }

    @Test
    public void shouldFindById() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();

        TestObserver<RateLimit> observer = repository.findById(createdRateLimit.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));
        assertEqualsTo(rateLimit, observer);
    }

    @Test
    public void shouldFindByCriteria() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();
        RateLimitCriteria criteria = new RateLimitCriteria.Builder()
                .userId(createdRateLimit.getUserId())
                .factorId(createdRateLimit.getFactorId())
                .client(createdRateLimit.getClient())
                .build();

        TestObserver<RateLimit> observer = repository.findByCriteria(criteria).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));
        assertEqualsTo(rateLimit, observer);
    }

    @Test
    public void shouldNotFindByCriteria_invalidFactorId() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();
        RateLimitCriteria criteria = new RateLimitCriteria.Builder()
                .userId(createdRateLimit.getUserId())
                .factorId("invalid-factor-id")
                .client(createdRateLimit.getClient())
                .build();

        TestObserver<RateLimit> observer = repository.findByCriteria(criteria).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdate() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();

        TestObserver<RateLimit> observer = repository.findById(createdRateLimit.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));

        RateLimit updatableRateLimit = createRateLimit();
        updatableRateLimit.setId(createdRateLimit.getId());
        updatableRateLimit.setTokenLeft(999);
        updatableRateLimit.setAllowRequest(true);

        TestObserver<RateLimit> updatedObserver = repository.update(updatableRateLimit).test();
        updatedObserver.awaitDone(10, TimeUnit.SECONDS);

        updatedObserver.assertNoErrors();
        updatedObserver.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));
        updatedObserver.assertValue(obj -> obj.getTokenLeft() == updatableRateLimit.getTokenLeft());
        updatedObserver.assertValue(obj -> obj.isAllowRequest() == updatableRateLimit.isAllowRequest());
        assertEqualsTo(updatableRateLimit, updatedObserver);
    }

    @Test
    public void shouldDeleteById() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();

        TestObserver<RateLimit> observer = repository.findById(createdRateLimit.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));

        TestObserver<Void> deleteObserver = repository.delete(createdRateLimit.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<RateLimit> afterDeleteObserver = repository.findById(createdRateLimit.getId()).test();
        afterDeleteObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteObserver.assertNoErrors();
        afterDeleteObserver.assertNoValues();
    }

    @Test
    public void shouldDeleteByCriteria() {
        RateLimit rateLimit = createRateLimit();
        RateLimit createdRateLimit = repository.create(rateLimit).blockingGet();

        TestObserver<RateLimit> observer = repository.findById(createdRateLimit.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit.getId()));

        RateLimitCriteria criteria = new RateLimitCriteria.Builder()
                .userId(createdRateLimit.getUserId())
                .factorId(createdRateLimit.getFactorId())
                .client(createdRateLimit.getClient())
                .build();

        TestObserver<Void> deleteObserver = repository.delete(criteria).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<RateLimit> afterDeleteFindObserver = repository.findById(createdRateLimit.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();
    }

    @Test
    public void shouldDeleteByUser() {
        final String userId= "123-xyz";
        RateLimit rateLimit1 = createRateLimit();
        rateLimit1.setUserId(userId);
        RateLimit createdRateLimit1 = repository.create(rateLimit1).blockingGet();
        TestObserver<RateLimit> observer = repository.findById(createdRateLimit1.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit1.getId()));
        observer.assertValue(obj -> obj.getUserId().equals(userId));

        RateLimit rateLimit2 = createRateLimit();
        rateLimit2.setUserId(userId);
        RateLimit createdRateLimit2 = repository.create(rateLimit2).blockingGet();
        TestObserver<RateLimit> observer2 = repository.findById(createdRateLimit2.getId()).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertValue(obj -> obj.getId().equals(createdRateLimit2.getId()));
        observer2.assertValue(obj -> obj.getUserId().equals(userId));

        TestObserver<Void> deleteObserver = repository.deleteByUser(userId).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<RateLimit> afterDeleteFindObserver = repository.findById(createdRateLimit1.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();

        TestObserver<RateLimit> afterDeleteFindObserver2 = repository.findById(createdRateLimit2.getId()).test();
        afterDeleteFindObserver2.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver2.assertNoErrors();
        afterDeleteFindObserver2.assertNoValues();
    }

    @Test
    public void shouldDeleteByDomain() {
        final String domainId= "123-xyz";
        RateLimit rateLimit1 = createRateLimit();
        rateLimit1.setReferenceId(domainId);
        RateLimit createdRateLimit1 = repository.create(rateLimit1).blockingGet();
        TestObserver<RateLimit> observer = repository.findById(createdRateLimit1.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(createdRateLimit1.getId()));
        observer.assertValue(obj -> obj.getReferenceId().equals(domainId));

        RateLimit rateLimit2 = createRateLimit();
        rateLimit2.setReferenceId(domainId);
        RateLimit createdRateLimit2 = repository.create(rateLimit2).blockingGet();
        TestObserver<RateLimit> observer2 = repository.findById(createdRateLimit2.getId()).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertValue(obj -> obj.getId().equals(createdRateLimit2.getId()));
        observer2.assertValue(obj -> obj.getReferenceId().equals(domainId));

        TestObserver<Void> deleteObserver = repository.deleteByDomain(domainId, ReferenceType.DOMAIN).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        TestObserver<RateLimit> afterDeleteFindObserver = repository.findById(createdRateLimit1.getId()).test();
        afterDeleteFindObserver.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver.assertNoErrors();
        afterDeleteFindObserver.assertNoValues();

        TestObserver<RateLimit> afterDeleteFindObserver2 = repository.findById(createdRateLimit2.getId()).test();
        afterDeleteFindObserver2.awaitDone(10, TimeUnit.SECONDS);
        afterDeleteFindObserver2.assertNoErrors();
        afterDeleteFindObserver2.assertNoValues();
    }

    private void assertEqualsTo(RateLimit rateLimit, TestObserver<RateLimit> observer) {
        observer.assertValue(observable -> observable.getUserId().equals(rateLimit.getUserId()));
        observer.assertValue(observable -> observable.getClient().equals(rateLimit.getClient()));
        observer.assertValue(observable -> observable.getFactorId().equals(rateLimit.getFactorId()));
        observer.assertValue(observable -> observable.getTokenLeft() == rateLimit.getTokenLeft());
    }

    private RateLimit createRateLimit() {
        final RateLimit rateLimit = new RateLimit();
        final String random = UUID.randomUUID().toString();
        rateLimit.setClient("client-id" + random);
        rateLimit.setUserId("user-id" + random);
        rateLimit.setFactorId("factor-id" + random);
        rateLimit.setTokenLeft(10);
        rateLimit.setAllowRequest(true);
        final Date date = new Date();
        rateLimit.setCreatedAt(date);
        rateLimit.setUpdatedAt(date);
        rateLimit.setReferenceId("domain-id" + random);
        rateLimit.setReferenceType(ReferenceType.DOMAIN);

        return rateLimit;
    }
}
