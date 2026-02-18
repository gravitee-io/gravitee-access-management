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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of the email staging batch processor.
 * <p>
 * When bulk email mode is enabled, this component starts a scheduled task that periodically
 * fetches staged emails from the repository and dispatches them via the {@link EmailService}.
 * It handles lease acquisition to prevent concurrent processing across multiple gateway nodes.
 * </p>
 *
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EmailStagingProcessor implements InitializingBean, DisposableBean {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_PERIOD_IN_SECONDS = 5;
    public static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final EmailStagingService emailStagingService;
    private final EmailService emailService;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final ApplicationService applicationService;
    private final Domain domain;
    private final int batchSize;
    private final int batchPeriod;
    private final int maxAttempts;
    private final boolean enabled;

    private UserRepository userRepository;
    private ScheduledExecutorService scheduledExecutor;

    public EmailStagingProcessor(
            EmailStagingService emailStagingService,
            EmailService emailService,
            DataPlaneRegistry dataPlaneRegistry,
            ApplicationService applicationService,
            Domain domain,
            int batchSize,
            int batchPeriod,
            int maxAttempts,
            boolean enabled) {
        this.emailStagingService = emailStagingService;
        this.emailService = emailService;
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.applicationService = applicationService;
        this.domain = domain;
        this.batchSize = batchSize;
        this.batchPeriod = batchPeriod;
        this.maxAttempts = maxAttempts;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        this.userRepository = dataPlaneRegistry.getUserRepository(domain);
        if (enabled) {
            this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "EmailStagingProcessor-" + domain.getId());
                thread.setDaemon(true);
                return thread;
            });
            scheduleEmailStagingProcessing();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (this.scheduledExecutor != null) {
            log.debug("Shutting down email staging processor for domain {}", domain.getName());
            this.scheduledExecutor.shutdown();
            try {
                if (!this.scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Email staging processor did not terminate gracefully, forcing shutdown");
                    this.scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for email staging processor shutdown", e);
                this.scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                emailStagingService.releaseLease(Reference.domain(domain.getId()))
                    .subscribe(
                            () -> log.debug("Successfully released emailStaging lease on domain {}", domain.getName()),
                            error -> log.warn("An error occurs while trying to release emailStaging lease on domain {}", domain.getName(), error));
            }
        }
    }

    /**
     * Fetches and processes a batch of staged emails for this domain.
     * <p>
     * Acquires a distributed lease before fetching to prevent concurrent processing across nodes.
     * If the lease cannot be acquired ({@link ActionLeaseException}), the batch is silently skipped
     * and will be retried on the next scheduled run.
     * </p>
     */
    public void processBatchOfStagingEmails() {
        log.debug("Start processing batch of {} staging emails for domain {}", batchSize, domain.getName());
        emailStagingService.acquireLeaseAndFetch(domain.asReference(), batchSize)
                .flatMapMaybe(stagingEmail ->
                        userRepository.findById(domain.asReference(), UserId.internal(stagingEmail.getUserId()))
                                .flatMap(user -> resolveClient(stagingEmail.getApplicationId())
                                        .map(client -> new EmailContainer(user, client, stagingEmail))
                                        .switchIfEmpty(Maybe.defer(() -> Maybe.just(new EmailContainer(user, null, stagingEmail))))
                                ).switchIfEmpty(Maybe.defer(() -> {
                                    log.warn("User {} not found for staging email {}, marking as processed",
                                            stagingEmail.getUserId(), stagingEmail.getId());
                                    stagingEmail.markAsProcessed();
                                    return emailStagingService.manageAfterProcessing(stagingEmail)
                                            .flatMapMaybe(ignored -> Maybe.<EmailContainer>empty())
                                            .onErrorResumeNext(error -> {
                                                log.warn("Unable to mark as processed the staging email {}", stagingEmail, error);
                                                return Maybe.empty();
                                            });
                                }))
                ).toList()
                .observeOn(Schedulers.io())
                .flattenStreamAsFlowable(containers -> emailService.batch(containers, maxAttempts).stream())
                .flatMapSingle(container -> emailStagingService.manageAfterProcessing(container.stagingEmail()))
                .count()
                .blockingSubscribe(
                        count -> {
                            if (count > 0) {
                                log.debug("Successfully processed {} staging emails", count);
                            }
                        },
                        error -> {
                            if (error instanceof ActionLeaseException) {
                                log.debug("Lease on emailStaging rejected, will retry in {} seconds", batchPeriod);
                            } else {
                                log.error("An error occurs while trying to process staging emails", error);
                            }
                        }
                );
    }

    /**
     * Schedules the batch processing task with a fixed delay between executions.
     * Using {@code scheduleWithFixedDelay} ensures that the next execution starts only
     * after the previous one completes, preventing concurrent runs.
     */
    private void scheduleEmailStagingProcessing() {
        log.debug("Scheduling email staging processing for domain {} with period {} seconds",
                domain.getName(), batchPeriod);
        this.scheduledExecutor.scheduleWithFixedDelay(
                () -> {
                    try {
                        processBatchOfStagingEmails();
                    } catch (Exception e) {
                        log.error("Unexpected error during email staging processing for domain {}",
                                domain.getName(), e);
                    }
                },
                batchPeriod,
                batchPeriod,
                TimeUnit.SECONDS
        );
    }

    /**
     * Resolves the {@link io.gravitee.am.model.oidc.Client} for a given application ID.
     * Returns empty if the application ID is blank, cannot be found, or belongs to a different domain.
     */
    private Maybe<io.gravitee.am.model.oidc.Client> resolveClient(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return Maybe.empty();
        }
        return applicationService.findById(applicationId)
                .filter(app -> domain.getId().equals(app.getDomain()))
                .map(Application::toClient)
                .onErrorResumeNext(error -> {
                    log.warn("Unable to resolve application {} for domain {}, email will be sent without client context",
                            applicationId, domain.getName(), error);
                    return Maybe.empty();
                });
    }
}
