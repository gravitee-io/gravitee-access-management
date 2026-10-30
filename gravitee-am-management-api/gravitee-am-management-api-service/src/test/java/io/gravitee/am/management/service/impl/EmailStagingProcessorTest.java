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
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.EmailStagingService;
import io.gravitee.am.management.service.model.StagedEmail;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainReadService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailStagingProcessorTest {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 2;

    @Mock
    private EmailStagingService emailStagingService;

    @Mock
    private EmailService emailService;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private DomainReadService domainService;

    @Mock
    private UserRepository userRepository;

    private EmailStagingProcessor cut;

    private final Domain domain = domain("domain-id");

    @BeforeEach
    void init() {
        cut = new EmailStagingProcessor(emailStagingService, emailService, dataPlaneRegistry, applicationService, domainService,
                true, true, BATCH_SIZE, MAX_ATTEMPTS, 5, 1);
        when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        when(domainService.findById("domain-id")).thenReturn(Maybe.just(domain));
        when(emailService.prepare(any(), anyList())).thenAnswer(invocation -> Single.just(invocation.getArgument(1)));
        when(emailStagingService.manageAfterProcessing(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() throws Exception {
        cut.stop();
    }

    @Test
    void shouldSendStagedEmailsAndPersistOutcome() {
        var staging = staging("staging-id", "user-id", "app-id");
        var user = user("user-id");
        var client = application("app-id", "domain-id");
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("domain-id")));
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE)).thenReturn(Flowable.just(staging));
        when(userRepository.findById(domain.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.just(user));
        when(applicationService.findById("app-id")).thenReturn(Maybe.just(client));
        doAnswer(invocation -> {
            List<StagedEmail> stagedEmails = invocation.getArgument(0);
            stagedEmails.forEach(stagedEmail -> stagedEmail.staging().markAsProcessed());
            return null;
        }).when(emailService).batch(anyList(), eq(MAX_ATTEMPTS));

        process();

        assertThat(batchedEmails()).singleElement()
                .returns(user, StagedEmail::user)
                .returns(client, StagedEmail::client)
                .returns(staging, StagedEmail::staging);
        verify(emailStagingService).manageAfterProcessing(staging);
        assertThat(staging.isProcessed()).isTrue();
    }

    @Test
    void shouldSendWithoutClientFromAnotherDomain() {
        var staging = staging("staging-id", "user-id", "app-id");
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("domain-id")));
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE)).thenReturn(Flowable.just(staging));
        when(userRepository.findById(domain.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.just(user("user-id")));
        when(applicationService.findById("app-id")).thenReturn(Maybe.just(application("app-id", "other-domain")));

        process();

        assertThat(batchedEmails()).singleElement().satisfies(stagedEmail -> assertThat(stagedEmail.client()).isNull());
    }

    @Test
    void shouldRemoveStagingOfUnknownUser() {
        var staging = staging("staging-id", "user-id", null);
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("domain-id")));
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE)).thenReturn(Flowable.just(staging));
        when(userRepository.findById(domain.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.empty());

        process();

        assertThat(staging.isProcessed()).isTrue();
        verify(emailStagingService).manageAfterProcessing(staging);
        assertThat(batchedEmails()).isEmpty();
    }

    @Test
    void shouldRemoveStagingOfUnknownDomain() {
        var staging = staging("staging-id", "user-id", null);
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("deleted-domain")));
        when(domainService.findById("deleted-domain")).thenReturn(Maybe.empty());
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("deleted-domain"), BATCH_SIZE)).thenReturn(Flowable.just(staging));

        process();

        assertThat(staging.isProcessed()).isTrue();
        verify(emailStagingService).manageAfterProcessing(staging);
        verify(emailService, never()).batch(anyList(), any(Integer.class));
    }

    @Test
    void shouldSkipDomainUntilLeaseHeldByAnotherNodeExpires() {
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("domain-id")));
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE))
                .thenReturn(Flowable.error(new ActionLeaseException("lease held", Duration.ofMinutes(10))));

        process();
        process();

        verify(emailStagingService, times(1)).acquireLeaseAndFetch(any(), any(Integer.class));
    }

    @Test
    void shouldProcessNextDomainWhenOneFails() {
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("broken-domain"), Reference.domain("domain-id")));
        when(domainService.findById("broken-domain")).thenReturn(Maybe.error(new IllegalStateException("db down")));
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE)).thenReturn(Flowable.just(staging("staging-id", "user-id", null)));
        when(userRepository.findById(domain.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.just(user("user-id")));

        process();

        assertThat(batchedEmails()).hasSize(1);
    }

    @Test
    void shouldIgnoreNonDomainReferences() {
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(new Reference(ReferenceType.ORGANIZATION, "org-id")));

        process();

        verify(emailStagingService, never()).acquireLeaseAndFetch(any(), any(Integer.class));
    }

    @Test
    void shouldReleaseLeaseOnlyOnceInFlightBatchIsSent() throws Exception {
        cut = new EmailStagingProcessor(emailStagingService, emailService, dataPlaneRegistry, applicationService, domainService,
                true, true, BATCH_SIZE, MAX_ATTEMPTS, 1, 1);
        var batchStarted = new CountDownLatch(1);
        var batchReleased = new CountDownLatch(1);
        when(emailStagingService.listReferences()).thenReturn(Flowable.just(Reference.domain("domain-id")), Flowable.empty());
        when(emailStagingService.acquireLeaseAndFetch(Reference.domain("domain-id"), BATCH_SIZE)).thenReturn(Flowable.just(staging("staging-id", "user-id", null)));
        when(userRepository.findById(domain.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.just(user("user-id")));
        when(emailStagingService.releaseLease(Reference.domain("domain-id"))).thenReturn(Completable.complete());
        doAnswer(invocation -> {
            batchStarted.countDown();
            batchReleased.await(10, TimeUnit.SECONDS);
            return null;
        }).when(emailService).batch(anyList(), eq(MAX_ATTEMPTS));

        cut.start();
        assertThat(batchStarted.await(10, TimeUnit.SECONDS)).isTrue();
        var stop = CompletableFuture.runAsync(() -> {
            try {
                cut.stop();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        verify(emailStagingService, after(300).never()).releaseLease(any());
        batchReleased.countDown();
        stop.get(10, TimeUnit.SECONDS);
        verify(emailStagingService).manageAfterProcessing(any());
        verify(emailStagingService).releaseLease(Reference.domain("domain-id"));
    }

    private void process() {
        cut.processStagedEmails().test().awaitDone(10, TimeUnit.SECONDS).assertComplete();
    }

    @SuppressWarnings("unchecked")
    private List<StagedEmail> batchedEmails() {
        ArgumentCaptor<List<StagedEmail>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailService, atMostOnce()).batch(captor.capture(), eq(MAX_ATTEMPTS));
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private static EmailStaging staging(String id, String userId, String applicationId) {
        var staging = new EmailStaging();
        staging.setId(id);
        staging.setUserId(userId);
        staging.setApplicationId(applicationId);
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setReferenceId("domain-id");
        staging.setEmailTemplateName(Template.REGISTRATION_CONFIRMATION.name());
        return staging;
    }

    private static User user(String id) {
        var user = new User();
        user.setId(id);
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-id");
        return user;
    }

    private static Application application(String id, String domainId) {
        var application = new Application();
        application.setId(id);
        application.setDomain(domainId);
        return application;
    }

    private static Domain domain(String id) {
        var domain = new Domain();
        domain.setId(id);
        return domain;
    }
}
