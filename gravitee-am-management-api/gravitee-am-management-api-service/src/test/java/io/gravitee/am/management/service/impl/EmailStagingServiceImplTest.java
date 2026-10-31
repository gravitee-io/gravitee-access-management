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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.management.service.ActionLeaseService;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.common.EmailStagingRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailStagingServiceImplTest {

    private static final Reference DOMAIN = Reference.domain("domain-id");
    private static final String LEASE_ACTION = "process_email_staging:domain-id";

    @Mock
    private EmailStagingRepository emailStagingRepository;

    @Mock
    private ActionLeaseService actionLeaseService;

    private EmailStagingServiceImpl cut;

    @BeforeEach
    void init() {
        cut = new EmailStagingServiceImpl(emailStagingRepository, actionLeaseService, 60);
    }

    @Test
    void shouldPushUserAndClient() {
        var user = new User();
        user.setId("user-id");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-id");
        var client = new Application();
        client.setId("app-id");
        when(emailStagingRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        cut.push(user, client, Template.REGISTRATION_CONFIRMATION).test().assertComplete();

        var captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository).create(captor.capture());
        assertThat(captor.getValue())
                .returns("user-id", EmailStaging::getUserId)
                .returns("app-id", EmailStaging::getApplicationId)
                .returns(ReferenceType.DOMAIN, EmailStaging::getReferenceType)
                .returns("domain-id", EmailStaging::getReferenceId)
                .returns("REGISTRATION_CONFIRMATION", EmailStaging::getEmailTemplateName)
                .returns(0, EmailStaging::getAttempts);
    }

    @Test
    void shouldPushWithoutClient() {
        var user = new User();
        user.setId("user-id");
        user.setReferenceId("domain-id");
        when(emailStagingRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        cut.push(user, null, Template.REGISTRATION_VERIFY).test().assertComplete();

        var captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository).create(captor.capture());
        assertThat(captor.getValue().getApplicationId()).isNull();
        assertThat(captor.getValue().getEmailTemplateName()).isEqualTo("REGISTRATION_VERIFY");
    }

    @Test
    void shouldFailPushWhenRepositoryFails() {
        when(emailStagingRepository.create(any())).thenReturn(Single.error(new IllegalStateException("db down")));

        cut.push(new User(), null, Template.REGISTRATION_CONFIRMATION).test().assertError(IllegalStateException.class);
    }

    @Test
    void shouldFetchOldestEmailsWhenLeaseAcquired() {
        var staging = new EmailStaging();
        when(actionLeaseService.acquireLease(LEASE_ACTION, Duration.ofSeconds(60))).thenReturn(Maybe.just(new ActionLease()));
        when(emailStagingRepository.findOldestByUpdateDate(DOMAIN, 10)).thenReturn(Flowable.just(staging));

        cut.acquireLeaseAndFetch(DOMAIN, 10).test().assertValues(staging);
    }

    @Test
    void shouldFailWithLeaseExceptionWhenLeaseHeldByAnotherNode() {
        when(actionLeaseService.acquireLease(LEASE_ACTION, Duration.ofSeconds(60))).thenReturn(Maybe.empty());

        cut.acquireLeaseAndFetch(DOMAIN, 10).test().assertError(ActionLeaseException.class);
        verify(emailStagingRepository, never()).findOldestByUpdateDate(any(), any(Integer.class));
    }

    @Test
    void shouldDeleteProcessedEmail() {
        var staging = new EmailStaging();
        staging.setId("staging-id");
        staging.markAsProcessed();
        when(emailStagingRepository.delete("staging-id")).thenReturn(Completable.complete());

        cut.manageAfterProcessing(staging).test().assertValue(staging);
        verify(emailStagingRepository, never()).updateAttempts(any(), any(Integer.class));
    }

    @Test
    void shouldUpdateAttemptsOfUnprocessedEmail() {
        var staging = new EmailStaging();
        staging.setId("staging-id");
        staging.setAttempts(1);
        when(emailStagingRepository.updateAttempts("staging-id", 1)).thenReturn(Single.just(staging));

        cut.manageAfterProcessing(staging).test().assertValue(staging);
        verify(emailStagingRepository, never()).delete(any(String.class));
    }

    @Test
    void shouldIgnoreRepositoryErrorAfterProcessing() {
        var staging = new EmailStaging();
        staging.setId("staging-id");
        staging.markAsProcessed();
        when(emailStagingRepository.delete("staging-id")).thenReturn(Completable.error(new IllegalStateException("db down")));

        cut.manageAfterProcessing(staging).test().assertValue(staging);
    }

    @Test
    void shouldReleaseLeaseAndIgnoreErrors() {
        when(actionLeaseService.releaseLease(LEASE_ACTION)).thenReturn(Completable.error(new IllegalStateException("db down")));

        cut.releaseLease(DOMAIN).test().assertComplete();
    }
}
