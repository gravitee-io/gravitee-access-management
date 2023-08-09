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

import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("ALL")
public class PasswordHistoryRepositoryTest extends AbstractManagementTest {
    public static final String REF_ID = randomUUID().toString();
    @Autowired
    private PasswordHistoryRepository repository;

    @Test
    public void shouldCreatePasswordHistory() {
        var history = buildPasswordHistory();
        var testObserver = repository.create(history).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(history, testObserver);
    }

    @Test
    public void shouldDeletePasswordHistory() {
        var created = repository.create(buildPasswordHistory()).blockingGet();
        assertNotNull(created.getId());
        var testObserver = repository.delete(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdatePasswordHistory() {
        var created = repository.create(buildPasswordHistory()).blockingGet();
        created.setUpdatedAt(new Date());
        var updated = repository.update(created).blockingGet();
        var testObserver = repository.findById(created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(updated, testObserver);
    }

    @Test
    public void shouldFindPasswordHistoryForUser() {
        var userId = randomUUID().toString();
        var password = "Password";
        for (int i = 0; i < 5; i++) {
            PasswordHistory history = buildPasswordHistory(userId, password + i);
            repository.create(history).blockingGet();
        }
        var testObserver = repository.findUserHistory(ReferenceType.DOMAIN, REF_ID, userId).toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(passwordHistories -> passwordHistories.stream().allMatch(p -> p.getUserId()
                .equals(userId)));
    }

    @Test
    public void shouldFindPasswordHistoryForDomain() {
        int expectedHistoriesForDomain = 3;
        for (int i = 0; i < expectedHistoriesForDomain; i++) {
            PasswordHistory history = buildPasswordHistory(randomUUID().toString(), "password");
            repository.create(history).blockingGet();
        }
        var testSubscriber = repository.findByReference(ReferenceType.DOMAIN, REF_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(expectedHistoriesForDomain);
    }

    @Test
    public void shouldDeletePasswordHistoryForDomain() {
        for (int i = 0; i < 5; i++) {
            PasswordHistory history = buildPasswordHistory(randomUUID().toString(), "password");
            repository.create(history).blockingGet();
        }
        var testObserver = repository.deleteByReference(ReferenceType.DOMAIN, REF_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        var testSubscriber = repository.findByReference(ReferenceType.DOMAIN, REF_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(0);
    }

    @Test
    public void shouldDeletePasswordHistoryForUser() {
        var userId = randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            PasswordHistory history = buildPasswordHistory(userId, "password");
            repository.create(history).blockingGet();
        }
        var testObserver = repository.deleteByUserId(userId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        var testSubscriber = repository.findUserHistory(ReferenceType.DOMAIN, REF_ID, userId).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(0);
    }

    @Test
    public void shouldFindById() {
        var passwordHistory = repository.create(buildPasswordHistory()).blockingGet();
        var testObserver = repository.findById(passwordHistory.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(passwordHistory, testObserver);
    }

    private PasswordHistory buildPasswordHistory() {
        return buildPasswordHistory(randomUUID().toString(), "Password123");
    }

    private PasswordHistory buildPasswordHistory(String userId, String password) {
        var history = new PasswordHistory();
        history.setReferenceId(REF_ID);
        history.setReferenceType(ReferenceType.DOMAIN);
        history.setCreatedAt(new Date());
        history.setUpdatedAt(new Date());
        history.setUserId(userId);
        history.setPassword(password);
        return history;
    }

    private void assertEqualsTo(PasswordHistory history, TestObserver<PasswordHistory> testObserver) {
        testObserver.assertValue(g -> g.getReferenceId().equals(history.getReferenceId()));
        testObserver.assertValue(g -> g.getReferenceType().equals(history.getReferenceType()));
        testObserver.assertValue(g -> g.getPassword().equals(history.getPassword()));
        testObserver.assertValue(g -> g.getUserId().equals(history.getUserId()));
    }
}
