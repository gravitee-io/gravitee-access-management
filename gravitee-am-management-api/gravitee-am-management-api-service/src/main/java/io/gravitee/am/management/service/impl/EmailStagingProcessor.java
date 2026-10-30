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
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.EmailStagingService;
import io.gravitee.am.management.service.model.StagedEmail;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@CustomLog
public class EmailStagingProcessor extends AbstractService<EmailStagingProcessor> {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    public static final int DEFAULT_PERIOD_IN_SECONDS = 5;
    public static final int DEFAULT_EXECUTOR_THREADS = 2;
    private static final int STOP_TIMEOUT_IN_SECONDS = 10;

    private final EmailStagingService emailStagingService;
    private final EmailService emailService;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final ApplicationService applicationService;
    private final DomainReadService domainService;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final int period;
    private final ExecutorService sendExecutor;
    private final Scheduler sendScheduler;

    private final Map<String, Long> leaseExpiries = new ConcurrentHashMap<>();

    private ScheduledExecutorService ticker;

    public EmailStagingProcessor(@Lazy EmailStagingService emailStagingService,
                                 @Lazy EmailService emailService,
                                 @Lazy DataPlaneRegistry dataPlaneRegistry,
                                 @Lazy ApplicationService applicationService,
                                 @Lazy DomainReadService domainService,
                                 @Value("${email.enabled:false}") boolean emailEnabled,
                                 @Value("${email.bulk.enabled:false}") boolean bulkEnabled,
                                 @Value("${email.bulk.batch:" + DEFAULT_BATCH_SIZE + "}") int batchSize,
                                 @Value("${email.bulk.attempts:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts,
                                 @Value("${email.bulk.period:" + DEFAULT_PERIOD_IN_SECONDS + "}") int period,
                                 @Value("${email.bulk.executor.threads:" + DEFAULT_EXECUTOR_THREADS + "}") int executorThreads) {
        this.emailStagingService = emailStagingService;
        this.emailService = emailService;
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.applicationService = applicationService;
        this.domainService = domainService;
        this.enabled = emailEnabled && bulkEnabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.period = period;
        this.sendExecutor = Executors.newFixedThreadPool(executorThreads, runnable -> {
            var thread = new Thread(runnable, "email-staging-sender");
            thread.setDaemon(true);
            return thread;
        });
        this.sendScheduler = Schedulers.from(sendExecutor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (enabled) {
            log.info("Email staging processing scheduled every {} seconds", period);
            ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, "email-staging-processor");
                thread.setDaemon(true);
                return thread;
            });
            ticker.scheduleWithFixedDelay(this::tick, period, period, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (ticker != null) {
            ticker.shutdown();
            if (ticker.awaitTermination(STOP_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                Flowable.fromIterable(leaseExpiries.keySet())
                        .concatMapCompletable(domainId -> emailStagingService.releaseLease(Reference.domain(domainId)))
                        .timeout(5, TimeUnit.SECONDS)
                        .onErrorComplete()
                        .blockingAwait();
            } else {
                log.warn("Email staging processing still running, leases will be released on expiry");
            }
        }
        sendExecutor.shutdown();
    }

    private void tick() {
        processStagedEmails().blockingSubscribe(
                () -> log.debug("Email staging processing completed"),
                error -> log.error("An error occurs while processing staging emails", error));
    }

    Completable processStagedEmails() {
        return emailStagingService.listReferences()
                .filter(reference -> reference.type() == ReferenceType.DOMAIN && leaseMayBeAvailable(reference.id()))
                .concatMapCompletable(this::processDomain);
    }

    private Completable processDomain(Reference reference) {
        return domainService.findById(reference.id())
                .flatMapSingle(this::sendBatch)
                .switchIfEmpty(Single.defer(() -> dropBatch(reference)))
                .doOnSuccess(count -> {
                    leaseExpiries.put(reference.id(), 0L);
                    if (count > 0) {
                        log.debug("Processed {} staging emails for domain {}", count, reference.id());
                    }
                })
                .ignoreElement()
                .onErrorResumeNext(error -> {
                    if (error instanceof ActionLeaseException leaseException) {
                        leaseExpiries.put(reference.id(), System.currentTimeMillis() + leaseException.getLeaseDuration().toMillis());
                        log.debug("Lease on email staging for domain {} rejected", reference.id());
                    } else {
                        log.error("An error occurs while processing staging emails for domain {}", reference.id(), error);
                    }
                    return Completable.complete();
                });
    }

    private Single<Long> sendBatch(Domain domain) {
        return emailStagingService.acquireLeaseAndFetch(domain.asReference(), batchSize)
                .concatMapMaybe(staging -> resolve(domain, staging))
                .toList()
                .flatMap(stagedEmails -> emailService.prepare(domain, stagedEmails))
                .observeOn(sendScheduler)
                .flattenAsFlowable(stagedEmails -> {
                    emailService.batch(stagedEmails, maxAttempts);
                    return stagedEmails;
                })
                .concatMapSingle(stagedEmail -> emailStagingService.manageAfterProcessing(stagedEmail.staging()))
                .count();
    }

    private Single<Long> dropBatch(Reference reference) {
        log.warn("Domain {} not found, its staging emails will be removed", reference.id());
        return emailStagingService.acquireLeaseAndFetch(reference, batchSize)
                .concatMapSingle(this::markAsProcessed)
                .count();
    }

    private Maybe<StagedEmail> resolve(Domain domain, EmailStaging staging) {
        return dataPlaneRegistry.getUserRepository(domain).findById(domain.asReference(), UserId.internal(staging.getUserId()))
                .flatMap(user -> resolveClient(domain, staging.getApplicationId())
                        .map(client -> new StagedEmail(user, client, staging))
                        .defaultIfEmpty(new StagedEmail(user, null, staging))
                        .toMaybe())
                .switchIfEmpty(Maybe.defer(() -> {
                    log.warn("User {} not found for staging email {}, it will be removed", staging.getUserId(), staging.getId());
                    return markAsProcessed(staging).flatMapMaybe(ignored -> Maybe.empty());
                }));
    }

    private Maybe<Application> resolveClient(Domain domain, String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return Maybe.empty();
        }
        return applicationService.findById(applicationId)
                .filter(application -> domain.getId().equals(application.getDomain()))
                .onErrorResumeNext(error -> {
                    log.warn("Unable to resolve application {} for domain {}, email will be sent without client context", applicationId, domain.getId(), error);
                    return Maybe.empty();
                });
    }

    private Single<EmailStaging> markAsProcessed(EmailStaging staging) {
        staging.markAsProcessed();
        return emailStagingService.manageAfterProcessing(staging);
    }

    private boolean leaseMayBeAvailable(String domainId) {
        return System.currentTimeMillis() > leaseExpiries.getOrDefault(domainId, 0L);
    }
}
