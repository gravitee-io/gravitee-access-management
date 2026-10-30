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

import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.EmailStagingRepository;
import io.gravitee.am.repository.management.AbstractManagementTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class EmailStagingRepositoryTest extends AbstractManagementTest {

    @Autowired
    private EmailStagingRepository repository;

    @Test
    public void shouldCreate() {
        var domainId = randomDomainId();
        var emailStaging = emailStaging(domainId);

        var created = repository.create(emailStaging).blockingGet();

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(emailStaging.getUserId());
        assertThat(created.getApplicationId()).isEqualTo(emailStaging.getApplicationId());
        assertThat(created.getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(created.getReferenceId()).isEqualTo(domainId);
        assertThat(created.getEmailTemplateName()).isEqualTo(emailStaging.getEmailTemplateName());
        assertThat(created.getAttempts()).isZero();
    }

    @Test
    public void shouldCreateWithoutApplication() {
        var emailStaging = emailStaging(randomDomainId());
        emailStaging.setApplicationId(null);

        var created = repository.create(emailStaging).blockingGet();

        assertThat(created.getApplicationId()).isNull();
    }

    @Test
    public void shouldDelete() {
        var domainId = randomDomainId();
        var created = repository.create(emailStaging(domainId)).blockingGet();

        repository.delete(created.getId()).blockingAwait();

        assertThat(repository.findOldestByUpdateDate(Reference.domain(domainId), 10).toList().blockingGet()).isEmpty();
    }

    @Test
    public void shouldDeleteMultiple() {
        var domainId = randomDomainId();
        var first = repository.create(emailStaging(domainId)).blockingGet();
        var second = repository.create(emailStaging(domainId)).blockingGet();
        var kept = repository.create(emailStaging(domainId)).blockingGet();

        repository.delete(List.of(first.getId(), second.getId())).blockingAwait();

        assertThat(repository.findOldestByUpdateDate(Reference.domain(domainId), 10).toList().blockingGet())
                .extracting(EmailStaging::getId)
                .containsExactly(kept.getId());
    }

    @Test
    public void shouldDeleteNothingWithEmptyList() {
        repository.delete(List.of()).test().awaitDone(10, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    public void shouldFindOldestByUpdateDateWithinLimitAndReference() throws InterruptedException {
        var domainId = randomDomainId();
        var first = repository.create(emailStaging(domainId)).blockingGet();
        Thread.sleep(50);
        var second = repository.create(emailStaging(domainId)).blockingGet();
        Thread.sleep(50);
        repository.create(emailStaging(domainId)).blockingGet();
        repository.create(emailStaging(randomDomainId())).blockingGet();

        var oldest = repository.findOldestByUpdateDate(Reference.domain(domainId), 2).toList().blockingGet();

        assertThat(oldest).extracting(EmailStaging::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    public void shouldUpdateAttemptsAndMoveToTheEnd() throws InterruptedException {
        var domainId = randomDomainId();
        var first = repository.create(emailStaging(domainId)).blockingGet();
        Thread.sleep(50);
        var second = repository.create(emailStaging(domainId)).blockingGet();
        Thread.sleep(50);

        var updated = repository.updateAttempts(first.getId(), 1).blockingGet();

        assertThat(updated.getAttempts()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isAfter(first.getUpdatedAt());
        assertThat(repository.findOldestByUpdateDate(Reference.domain(domainId), 2).toList().blockingGet())
                .extracting(EmailStaging::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    public void shouldListDistinctReferences() {
        var domainId1 = randomDomainId();
        var domainId2 = randomDomainId();
        repository.create(emailStaging(domainId1)).blockingGet();
        repository.create(emailStaging(domainId1)).blockingGet();
        repository.create(emailStaging(domainId2)).blockingGet();

        var references = repository.listReferences().toList().blockingGet();

        assertThat(references).containsExactlyInAnyOrder(Reference.domain(domainId1), Reference.domain(domainId2));
    }

    private static String randomDomainId() {
        return "domain-id-" + UUID.randomUUID();
    }

    private static EmailStaging emailStaging(String domainId) {
        var random = UUID.randomUUID().toString();
        var emailStaging = new EmailStaging();
        emailStaging.setUserId("user-id-" + random);
        emailStaging.setApplicationId("app-id-" + random);
        emailStaging.setReferenceType(ReferenceType.DOMAIN);
        emailStaging.setReferenceId(domainId);
        emailStaging.setEmailTemplateName("REGISTRATION_CONFIRMATION");
        return emailStaging;
    }
}
