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

import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailStagingRepositoryTest extends AbstractGatewayTest {
    @Autowired
    protected EmailStagingRepository repository;

    @Test
    public void shouldCreate() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);

        TestObserver<EmailStaging> observer = repository.create(emailStaging).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId() != null);
        observer.assertValue(obj -> obj.getCreatedAt() != null);
        observer.assertValue(obj -> obj.getUpdatedAt() != null);
        assertEqualsTo(emailStaging, observer);
    }

    @Test
    public void shouldCreateWithId() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);
        String id = UUID.randomUUID().toString();
        emailStaging.setId(id);

        TestObserver<EmailStaging> observer = repository.create(emailStaging).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getId().equals(id));
        assertEqualsTo(emailStaging, observer);
    }

    @Test
    public void shouldDelete() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);
        EmailStaging createdEmailStaging = repository.create(emailStaging).blockingGet();

        TestObserver<Void> deleteObserver = repository.delete(createdEmailStaging.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();
        deleteObserver.assertComplete();
    }

    @Test
    public void shouldDeleteMultiple() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging1 = createEmailStaging(domainId);
        EmailStaging createdEmailStaging1 = repository.create(emailStaging1).blockingGet();

        EmailStaging emailStaging2 = createEmailStaging(domainId);
        EmailStaging createdEmailStaging2 = repository.create(emailStaging2).blockingGet();

        EmailStaging emailStaging3 = createEmailStaging(domainId);
        EmailStaging createdEmailStaging3 = repository.create(emailStaging3).blockingGet();

        List<String> idsToDelete = Arrays.asList(
                createdEmailStaging1.getId(),
                createdEmailStaging2.getId()
        );

        TestObserver<Void> deleteObserver = repository.delete(idsToDelete).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();
        deleteObserver.assertComplete();
    }

    @Test
    public void shouldDeleteMultipleWithEmptyList() {
        TestObserver<Void> deleteObserver = repository.delete(Arrays.asList()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();
        deleteObserver.assertComplete();
    }

    @Test
    public void shouldFindOldestByUpdateDate() throws InterruptedException {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        // Create first email staging
        EmailStaging emailStaging1 = createEmailStaging(domainId);
        EmailStaging created1 = repository.create(emailStaging1).blockingGet();

        // Wait a bit to ensure different timestamps
        Thread.sleep(100);

        // Create second email staging
        EmailStaging emailStaging2 = createEmailStaging(domainId);
        EmailStaging created2 = repository.create(emailStaging2).blockingGet();

        // Wait a bit
        Thread.sleep(100);

        // Create third email staging
        EmailStaging emailStaging3 = createEmailStaging(domainId);
        EmailStaging created3 = repository.create(emailStaging3).blockingGet();

        // Find oldest 2 entries
        TestSubscriber<EmailStaging> observer = repository.findOldestByUpdateDate(Reference.domain(domainId), 2).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValueCount(2);

        // Verify they are ordered by update date (oldest first)
        List<EmailStaging> results = observer.values();
        observer.assertValueAt(0, obj -> obj.getId().equals(created1.getId()));
        observer.assertValueAt(1, obj -> obj.getId().equals(created2.getId()));
    }

    @Test
    public void shouldFindOldestByUpdateDateWithLimit() throws InterruptedException {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        // Create multiple email staging entries
        for (int i = 0; i < 5; i++) {
            EmailStaging emailStaging = createEmailStaging(domainId);
            repository.create(emailStaging).blockingGet();
            Thread.sleep(50);
        }

        // Find oldest 3 entries
        TestSubscriber<EmailStaging> observer = repository.findOldestByUpdateDate(Reference.domain(domainId), 3).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValueCount(3);
    }

    @Test
    public void shouldUpdateAttempts() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);
        emailStaging.setAttempts(0);
        EmailStaging created = repository.create(emailStaging).blockingGet();

        Date originalUpdatedAt = created.getUpdatedAt();

        // Update attempts
        int newAttempts = 5;
        TestObserver<EmailStaging> updateObserver = repository.updateAttempts(created.getId(), newAttempts).test();
        updateObserver.awaitDone(10, TimeUnit.SECONDS);

        updateObserver.assertNoErrors();
        updateObserver.assertValue(obj -> obj.getAttempts() == newAttempts);
        updateObserver.assertValue(obj -> obj.getUpdatedAt().after(originalUpdatedAt));
        updateObserver.assertValue(obj -> obj.getId().equals(created.getId()));
    }

    @Test
    public void shouldUpdateAttemptsMultipleTimes() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);
        emailStaging.setAttempts(0);
        EmailStaging created = repository.create(emailStaging).blockingGet();

        // First update
        EmailStaging updated1 = repository.updateAttempts(created.getId(), 1).blockingGet();
        assert updated1.getAttempts() == 1;

        // Second update
        EmailStaging updated2 = repository.updateAttempts(created.getId(), 2).blockingGet();
        assert updated2.getAttempts() == 2;
        assert updated2.getUpdatedAt().after(updated1.getUpdatedAt()) ||
               updated2.getUpdatedAt().equals(updated1.getUpdatedAt());

        // Third update
        EmailStaging updated3 = repository.updateAttempts(created.getId(), 3).blockingGet();
        assert updated3.getAttempts() == 3;
    }

    @Test
    public void shouldSortByUpdateDateAfterUpdate() throws InterruptedException {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        // Create first email staging
        EmailStaging emailStaging1 = createEmailStaging(domainId);
        EmailStaging created1 = repository.create(emailStaging1).blockingGet();

        Thread.sleep(100);

        // Create second email staging
        EmailStaging emailStaging2 = createEmailStaging(domainId);
        EmailStaging created2 = repository.create(emailStaging2).blockingGet();

        Thread.sleep(100);

        // Update the first one (should move it to the end)
        repository.updateAttempts(created1.getId(), 1).blockingGet();

        // Find oldest 2 entries
        TestSubscriber<EmailStaging> observer = repository.findOldestByUpdateDate(Reference.domain(domainId), 2).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValueCount(2);

        // After update, the second one should be first (oldest update date)
        List<EmailStaging> results = observer.values();
        observer.assertValueAt(0, obj -> obj.getId().equals(created2.getId()));
        observer.assertValueAt(1, obj -> obj.getId().equals(created1.getId()));
    }

    @Test
    public void shouldHandleNullableApplicationId() {
        final var domainId = "domain-id-" + UUID.randomUUID().toString();
        EmailStaging emailStaging = createEmailStaging(domainId);
        emailStaging.setApplicationId(null);

        TestObserver<EmailStaging> observer = repository.create(emailStaging).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertValue(obj -> obj.getApplicationId() == null);
    }

    private void assertEqualsTo(EmailStaging emailStaging, TestObserver<EmailStaging> observer) {
        observer.assertValue(obj -> obj.getUserId().equals(emailStaging.getUserId()));
        observer.assertValue(obj -> obj.getApplicationId() != null ?
                obj.getApplicationId().equals(emailStaging.getApplicationId()) :
                emailStaging.getApplicationId() == null);
        observer.assertValue(obj -> obj.getReferenceType().equals(emailStaging.getReferenceType()));
        observer.assertValue(obj -> obj.getReferenceId().equals(emailStaging.getReferenceId()));
        observer.assertValue(obj -> obj.getEmailTemplateName().equals(emailStaging.getEmailTemplateName()));
        observer.assertValue(obj -> obj.getAttempts() == emailStaging.getAttempts());
    }

    private EmailStaging createEmailStaging(String domainId) {
        final EmailStaging emailStaging = new EmailStaging();
        final String random = UUID.randomUUID().toString();

        emailStaging.setUserId("user-id-" + random);
        emailStaging.setApplicationId("app-id-" + random);
        emailStaging.setReferenceType(ReferenceType.DOMAIN);
        emailStaging.setReferenceId(domainId);
        emailStaging.setEmailTemplateName("registration-confirmation");
        emailStaging.setAttempts(0);

        return emailStaging;
    }
}
