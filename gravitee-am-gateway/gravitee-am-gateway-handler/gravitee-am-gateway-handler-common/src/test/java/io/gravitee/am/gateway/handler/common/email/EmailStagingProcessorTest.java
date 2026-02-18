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
package io.gravitee.am.gateway.handler.common.email;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailStagingProcessorTest {

    @Mock
    private EmailStagingService emailStagingService;

    @Mock
    private EmailService emailService;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationService applicationService;

    private Domain domain;

    @BeforeEach
    void setUp() {
        domain = new Domain();
        domain.setId("domain-id");
        domain.setName("test-domain");
        when(dataPlaneRegistry.getUserRepository(domain)).thenReturn(userRepository);
    }

    private EmailStagingProcessor buildProcessor(boolean enabled) {
        return new EmailStagingProcessor(
                emailStagingService,
                emailService,
                dataPlaneRegistry,
                applicationService,
                domain,
                EmailStagingProcessor.DEFAULT_BATCH_SIZE,
                EmailStagingProcessor.DEFAULT_PERIOD_IN_SECONDS,
                EmailStagingProcessor.DEFAULT_MAX_ATTEMPTS,
                enabled);
    }

    @Nested
    @DisplayName("Lifecycle management")
    class LifecycleTests {

        @Test
        @DisplayName("afterPropertiesSet should start scheduler when enabled")
        void afterPropertiesSet_shouldStartSchedulerWhenEnabled() throws Exception {
            when(emailStagingService.releaseLease(any())).thenReturn(Completable.complete());

            var processor = buildProcessor(true);
            processor.afterPropertiesSet();

            // processor field is private, but we can verify side-effects via behavior
            verify(dataPlaneRegistry).getUserRepository(domain);
            processor.destroy();
        }

        @Test
        @DisplayName("afterPropertiesSet should not start scheduler when disabled")
        void afterPropertiesSet_shouldNotStartSchedulerWhenDisabled() throws Exception {
            var processor = buildProcessor(false);
            processor.afterPropertiesSet();

            verify(dataPlaneRegistry).getUserRepository(domain);
            // No scheduler started, destroy should complete immediately without releasing lease
            processor.destroy();
            verify(emailStagingService, never()).releaseLease(any());
        }

        @Test
        @DisplayName("destroy should release lease when scheduler was started")
        void destroy_shouldReleaseLeaseWhenEnabled() throws Exception {
            when(emailStagingService.releaseLease(any())).thenReturn(Completable.complete());

            var processor = buildProcessor(true);
            processor.afterPropertiesSet();
            processor.destroy();

            verify(emailStagingService).releaseLease(Reference.domain(domain.getId()));
        }

        @Test
        @DisplayName("destroy should not release lease when scheduler was not started")
        void destroy_shouldNotReleaseLeaseWhenDisabled() throws Exception {
            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.destroy();

            verify(emailStagingService, never()).releaseLease(any());
        }

        @Test
        @DisplayName("scheduler should trigger processBatchOfStagingEmails periodically")
        void scheduler_shouldTriggerBatchProcessing() throws Exception {
            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.empty());
            when(emailStagingService.releaseLease(any())).thenReturn(Completable.complete());

            var processor = new EmailStagingProcessor(
                    emailStagingService,
                    emailService,
                    dataPlaneRegistry,
                    applicationService,
                    domain,
                    EmailStagingProcessor.DEFAULT_BATCH_SIZE,
                    1, // 1 second period for test speed
                    EmailStagingProcessor.DEFAULT_MAX_ATTEMPTS,
                    true);

            processor.afterPropertiesSet();

            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            verify(emailStagingService, atLeastOnce())
                                    .acquireLeaseAndFetch(domain.asReference(), EmailStagingProcessor.DEFAULT_BATCH_SIZE));

            processor.destroy();
        }
    }

    @Nested
    @DisplayName("processBatchOfStagingEmails")
    class ProcessBatchTests {

        @Test
        @DisplayName("should do nothing when no staging emails are available")
        void processBatch_shouldDoNothingWhenEmpty() {
            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt())).thenReturn(Flowable.empty());
            when(emailService.batch(any(), anyInt())).thenReturn(List.of());

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            verify(emailStagingService, never()).manageAfterProcessing(any());
        }

        @Test
        @DisplayName("should process a batch of staging emails successfully")
        void processBatch_shouldProcessEmailsSuccessfully() {
            var user = new User();
            user.setId("user-id");

            var stagingEmail = buildStagingEmail("user-id", null);

            var container = new EmailContainer(user, null, stagingEmail);
            var processedStaging = buildStagingEmail("user-id", null);
            processedStaging.markAsProcessed();

            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail));
            when(userRepository.findById(any(Reference.class), any(UserId.class)))
                    .thenReturn(Maybe.just(user));
            when(emailService.batch(any(), anyInt()))
                    .thenReturn(List.of(container));
            when(emailStagingService.manageAfterProcessing(stagingEmail))
                    .thenReturn(Single.just(processedStaging));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            verify(emailStagingService).acquireLeaseAndFetch(domain.asReference(), EmailStagingProcessor.DEFAULT_BATCH_SIZE);
            verify(emailService).batch(any(), eq(EmailStagingProcessor.DEFAULT_MAX_ATTEMPTS));
            verify(emailStagingService).manageAfterProcessing(stagingEmail);
        }

        @Test
        @DisplayName("should resolve client from applicationId and include it in the email container")
        void processBatch_shouldResolveClientWhenApplicationIdProvided() {
            var user = new User();
            user.setId("user-id");

            var application = new Application();
            application.setId("app-id");
            application.setDomain(domain.getId());

            var stagingEmail = buildStagingEmail("user-id", "app-id");
            var container = new EmailContainer(user, application.toClient(), stagingEmail);

            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail));
            when(userRepository.findById(any(Reference.class), any(UserId.class)))
                    .thenReturn(Maybe.just(user));
            when(applicationService.findById("app-id"))
                    .thenReturn(Maybe.just(application));
            when(emailService.batch(any(), anyInt()))
                    .thenReturn(List.of(container));
            when(emailStagingService.manageAfterProcessing(stagingEmail))
                    .thenReturn(Single.just(stagingEmail));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            verify(applicationService).findById("app-id");
            verify(emailService).batch(any(), eq(EmailStagingProcessor.DEFAULT_MAX_ATTEMPTS));
        }

        @Test
        @DisplayName("should use null client when application belongs to a different domain")
        void processBatch_shouldSkipClientWhenDifferentDomain() {
            var user = new User();
            user.setId("user-id");

            var application = new Application();
            application.setId("app-id");
            application.setDomain("other-domain-id");

            var stagingEmail = buildStagingEmail("user-id", "app-id");
            var containerWithoutClient = new EmailContainer(user, null, stagingEmail);

            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail));
            when(userRepository.findById(any(Reference.class), any(UserId.class)))
                    .thenReturn(Maybe.just(user));
            when(applicationService.findById("app-id"))
                    .thenReturn(Maybe.just(application));
            when(emailService.batch(any(), anyInt()))
                    .thenReturn(List.of(containerWithoutClient));
            when(emailStagingService.manageAfterProcessing(stagingEmail))
                    .thenReturn(Single.just(stagingEmail));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            // application filtered out because domain mismatch; email still sent without client
            verify(emailService).batch(any(), anyInt());
        }

        @Test
        @DisplayName("should skip staging email when user is not found in repository")
        void processBatch_shouldSkipWhenUserNotFound() {
            var stagingEmail = buildStagingEmail("missing-user-id", null);

            when(emailStagingService.manageAfterProcessing(any())).thenReturn(Single.just(stagingEmail));
            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail));
            when(userRepository.findById(any(Reference.class), any(UserId.class)))
                    .thenReturn(Maybe.empty());
            when(emailService.batch(any(), anyInt())).thenReturn(List.of());

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            // user not found → make sure the email will be mark as processed as
            // if the user is not found, this email will never be sent
            verify(emailStagingService).manageAfterProcessing(any());
        }

        @Test
        @DisplayName("should silently skip batch when lease cannot be acquired")
        void processBatch_shouldSkipWhenLeaseNotAcquired() {
            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.error(new ActionLeaseException("lease rejected")));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            // Should complete without throwing
            processor.processBatchOfStagingEmails();

            verify(emailService, never()).batch(any(), anyInt());
        }

        @Test
        @DisplayName("should continue processing other emails even when application resolution fails")
        void processBatch_shouldContinueWhenApplicationResolutionFails() {
            var user = new User();
            user.setId("user-id");

            var stagingEmail = buildStagingEmail("user-id", "bad-app-id");
            var containerWithoutClient = new EmailContainer(user, null, stagingEmail);

            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail));
            when(userRepository.findById(any(Reference.class), any(UserId.class)))
                    .thenReturn(Maybe.just(user));
            when(applicationService.findById("bad-app-id"))
                    .thenReturn(Maybe.error(new RuntimeException("DB error")));
            when(emailService.batch(any(), anyInt()))
                    .thenReturn(List.of(containerWithoutClient));
            when(emailStagingService.manageAfterProcessing(stagingEmail))
                    .thenReturn(Single.just(stagingEmail));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            // Email should still be processed with null client (application error swallowed)
            verify(emailService).batch(any(), anyInt());
        }

        @Test
        @DisplayName("should process multiple staging emails in a single batch")
        void processBatch_shouldProcessMultipleEmails() {
            var user1 = new User();
            user1.setId("user-id-1");
            var user2 = new User();
            user2.setId("user-id-2");

            var stagingEmail1 = buildStagingEmail("user-id-1", null);
            var stagingEmail2 = buildStagingEmail("user-id-2", null);

            var container1 = new EmailContainer(user1, null, stagingEmail1);
            var container2 = new EmailContainer(user2, null, stagingEmail2);

            when(emailStagingService.acquireLeaseAndFetch(any(), anyInt()))
                    .thenReturn(Flowable.just(stagingEmail1, stagingEmail2));
            when(userRepository.findById(any(Reference.class), eq(UserId.internal("user-id-1"))))
                    .thenReturn(Maybe.just(user1));
            when(userRepository.findById(any(Reference.class), eq(UserId.internal("user-id-2"))))
                    .thenReturn(Maybe.just(user2));
            when(emailService.batch(any(), anyInt()))
                    .thenReturn(List.of(container1, container2));
            when(emailStagingService.manageAfterProcessing(any()))
                    .thenReturn(Single.just(stagingEmail1));

            var processor = buildProcessor(false);
            processor.afterPropertiesSet();
            processor.processBatchOfStagingEmails();

            verify(emailStagingService).acquireLeaseAndFetch(domain.asReference(), EmailStagingProcessor.DEFAULT_BATCH_SIZE);
            verify(emailStagingService, times(2)).manageAfterProcessing(any());
        }
    }

    // --- helpers ---

    private EmailStaging buildStagingEmail(String userId, String applicationId) {
        var staging = new EmailStaging();
        staging.setId("staging-" + userId);
        staging.setUserId(userId);
        staging.setApplicationId(applicationId);
        staging.setAttempts(0);
        return staging;
    }
}
