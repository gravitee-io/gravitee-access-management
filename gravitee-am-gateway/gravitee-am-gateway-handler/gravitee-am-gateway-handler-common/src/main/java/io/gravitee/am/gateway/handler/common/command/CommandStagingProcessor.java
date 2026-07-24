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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.common.oidc.command.CommandConstants;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.CommandAuditBuilder;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-node batch dispatcher of staged OpenID Provider Commands, modeled on the
 * email staging processor: a periodic task acquires the domain action lease, fetches
 * unprocessed jobs and, for each opted-in client of the domain, mints a fresh command
 * token and POSTs it to the client's command_endpoint. Failed deliveries are retried
 * on subsequent runs (the dispatch period is the retry backoff) until the attempts cap.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class CommandStagingProcessor implements InitializingBean, DisposableBean {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final int DEFAULT_PERIOD_IN_SECONDS = 10;

    private final CommandStagingService commandStagingService;
    private final CommandTokenService commandTokenService;
    private final CommandTargetResolver commandTargetResolver;
    private final WebClient webClient;
    private final AuditService auditService;
    private final Domain domain;
    private final int batchSize;
    private final int batchPeriod;
    private final int maxAttempts;
    private final boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong leaseExpiry = new AtomicLong(0);

    private Disposable scheduledJob;

    public CommandStagingProcessor(CommandStagingService commandStagingService,
                                   CommandTokenService commandTokenService,
                                   CommandTargetResolver commandTargetResolver,
                                   WebClient webClient,
                                   AuditService auditService,
                                   Domain domain,
                                   int batchSize,
                                   int batchPeriod,
                                   int maxAttempts,
                                   boolean enabled) {
        this.commandStagingService = commandStagingService;
        this.commandTokenService = commandTokenService;
        this.commandTargetResolver = commandTargetResolver;
        this.webClient = webClient;
        this.auditService = auditService;
        this.domain = domain;
        this.batchSize = batchSize;
        this.batchPeriod = batchPeriod;
        this.maxAttempts = maxAttempts;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (enabled) {
            log.debug("Scheduling command staging processing for domain {} with period {} seconds", domain.getName(), batchPeriod);
            this.scheduledJob = Schedulers.io().schedulePeriodicallyDirect(
                    () -> {
                        try {
                            processBatchOfStagingCommands();
                        } catch (Exception e) {
                            log.error("Unexpected error during command staging processing for domain {}", domain.getName(), e);
                        }
                    },
                    batchPeriod,
                    batchPeriod,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void destroy() {
        if (enabled) {
            commandStagingService.releaseLease(domain.asReference())
                    .doFinally(() -> {
                        this.running.set(false);
                        if (scheduledJob != null && !scheduledJob.isDisposed()) {
                            scheduledJob.dispose();
                        }
                    })
                    .subscribe(
                            () -> log.debug("Successfully released commandStaging lease on domain {}", domain.getName()),
                            error -> log.warn("An error occurs while trying to release commandStaging lease on domain {}", domain.getName(), error));
        }
    }

    public boolean isRunning() {
        return this.running.get();
    }

    public void processBatchOfStagingCommands() {
        if (leaseDurationExpired() && noOngoingBatch()) {
            commandStagingService.acquireLeaseAndFetch(domain.asReference(), batchSize)
                    .concatMapSingle(this::processJob)
                    .count()
                    .doFinally(() -> this.running.set(false))
                    .subscribe(
                            count -> {
                                if (count > 0) {
                                    log.debug("Successfully processed {} staging commands", count);
                                }
                            },
                            error -> {
                                if (error instanceof ActionLeaseException leaseException) {
                                    long leaseDurationInMillis = leaseException.getLeaseDuration().toMillis();
                                    leaseExpiry.set(System.currentTimeMillis() + leaseDurationInMillis);
                                    log.debug("Lease on commandStaging rejected, will retry in {} milliseconds", leaseDurationInMillis);
                                } else {
                                    log.error("An error occurs while trying to process staging commands", error);
                                }
                            });
        }
    }

    private Single<CommandStaging> processJob(CommandStaging staging) {
        staging.incrementAttempts();
        final boolean finalAttempt = staging.getAttempts() >= maxAttempts;

        // targets are fully resolved before the first POST: if enumeration fails
        // (e.g. the CIMD document store is unreachable) nothing is delivered and the
        // whole job is retried, instead of leaving unpersisted partial deliveries
        return resolveTargets(staging)
                .toList()
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(client -> deliver(staging, client, finalAttempt))
                .toList()
                .flatMap(statuses -> {
                    final boolean allTerminal = statuses.stream().noneMatch(CommandDeliveryStatus.FAILED::equals);
                    if (allTerminal || finalAttempt) {
                        staging.markAsProcessed();
                    }
                    return commandStagingService.manageAfterProcessing(staging);
                });
    }

    /**
     * The resolver defines who is opted in; clients already in a terminal state from
     * a previous attempt are retry bookkeeping and filtered here.
     */
    private Flowable<Client> resolveTargets(CommandStaging staging) {
        return commandTargetResolver.resolveTargets()
                .filter(client -> !staging.isTerminal(client.getClientId()));
    }

    private Single<CommandDeliveryStatus> deliver(CommandStaging staging, Client client, boolean finalAttempt) {
        return commandTokenService.mintToken(staging, client)
                .flatMap(commandToken -> post(client.getCommandEndpoint(), commandToken))
                .onErrorReturn(error -> {
                    log.debug("Command {} delivery to client {} failed", staging.getId(), client.getClientId(), error);
                    return CommandDeliveryStatus.FAILED;
                })
                .doOnSuccess(status -> handleOutcome(staging, client, status, finalAttempt));
    }

    private Single<CommandDeliveryStatus> post(String commandEndpoint, String commandToken) {
        final var form = MultiMap.caseInsensitiveMultiMap().set(CommandConstants.COMMAND_TOKEN_PARAM, commandToken);
        return webClient.postAbs(commandEndpoint)
                .rxSendForm(form)
                .map(response -> CommandResponseClassifier.classify(response.statusCode(), response.bodyAsString()));
    }

    private void handleOutcome(CommandStaging staging, Client client, CommandDeliveryStatus status, boolean finalAttempt) {
        switch (status) {
            case DELIVERED -> {
                staging.markClientTerminal(client.getClientId());
                auditService.report(AuditBuilder.builder(CommandAuditBuilder.class).delivered(staging, client));
            }
            case UNKNOWN_ACCOUNT -> {
                staging.markClientTerminal(client.getClientId());
                auditService.report(AuditBuilder.builder(CommandAuditBuilder.class).delivered(staging, client).unknownAccount());
            }
            case FAILED -> {
                if (finalAttempt) {
                    auditService.report(AuditBuilder.builder(CommandAuditBuilder.class)
                            .deliveryFailed(staging, client, new IllegalStateException("Command delivery failed after " + staging.getAttempts() + " attempts")));
                    log.warn("Command {} delivery to client {} of domain {} abandoned after {} attempts",
                            staging.getId(), client.getClientId(), domain.getName(), staging.getAttempts());
                }
            }
        }
    }

    private boolean leaseDurationExpired() {
        return System.currentTimeMillis() > leaseExpiry.get();
    }

    private boolean noOngoingBatch() {
        // flag the processor as running to not start another task for this domain
        // until the current one is released
        return this.running.compareAndSet(false, true);
    }
}
