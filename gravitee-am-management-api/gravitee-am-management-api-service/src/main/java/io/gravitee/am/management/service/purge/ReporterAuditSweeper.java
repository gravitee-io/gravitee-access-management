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
package io.gravitee.am.management.service.purge;

import io.gravitee.am.management.service.ActionLeaseService;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class ReporterAuditSweeper implements ExpiredDataSweeper {

    public static final String LEASE_ACTION_AUDIT_SWEEPER = "auditSweeper";
    public static final int DEFAULT_AUDIT_SWEEPER_LEASE_DURATION_IN_SECONDS = 3600;
    public static final int DEFAULT_AUDIT_SWEEPER_GLOBAL_TIMEOUT_IN_SECONDS = 3600;

    private final AuditReporterManager auditReporterManager;

    private final ReporterService reporterService;

    private final ActionLeaseService actionLeaseService;

    public ReporterAuditSweeper(@Lazy AuditReporterManager auditReporterManager,
                                @Lazy ReporterService reporterService,
                                @Lazy ActionLeaseService actionLeaseService) {
        this.auditReporterManager = auditReporterManager;
        this.reporterService = reporterService;
        this.actionLeaseService = actionLeaseService;
    }

    @Value("${services.purge.audits.retention.days:0}")
    private int retentionDays;

    @Value("${services.purge.audits.leaseDuration:"+ DEFAULT_AUDIT_SWEEPER_LEASE_DURATION_IN_SECONDS +"}")
    private int leaseDuration;

    /**
     * Maximum duration (in seconds) of a single purge execution shared across all reporters. Once this
     * deadline is reached no further reporter is started and any running purge stops gracefully; the
     * remaining work resumes on the next scheduled execution.
     */
    @Value("${services.purge.audits.globalTimeout:"+ DEFAULT_AUDIT_SWEEPER_GLOBAL_TIMEOUT_IN_SECONDS +"}")
    private int globalTimeout;


    @Override
    public Completable purgeExpiredData() {
        log.info("Starting audit reporter data purge (retention: {} days, global timeout: {}s)", retentionDays, globalTimeout);

        // a single deadline is shared by every reporter so the whole execution cannot exceed the configured duration
        final Instant deadline = Instant.now().plusSeconds(globalTimeout);

        return actionLeaseService.acquireLease(LEASE_ACTION_AUDIT_SWEEPER, Duration.of(leaseDuration, ChronoUnit.SECONDS))
                // reporters are purged one at a time (concatMapCompletable) to limit the pressure on the backend
                .flatMapCompletable(lease -> reporterService.findAll()
                        .concatMapCompletable(reporter -> purgeReporter(reporter, deadline)
                                .onErrorResumeNext(error -> {
                                    log.error("Failed to purge audits for reporter {}/{}: {}",
                                            reporter.getName(), reporter.getId(), error.getMessage(), error);
                                    return Completable.complete();
                                }))
                        // the internal (platform) reporter is created in memory and is not returned by findAll(),
                        // so it must be purged explicitly otherwise its 'reporter_audits_PLATFORM' collection grows unbounded
                        .andThen(purgeInternalReporter(deadline)))
                .doOnComplete(() -> log.info("Audit reporter data purge completed"))
                .doOnError(error -> log.error("Audit reporter data purge failed", error));
    }

    private Completable purgeInternalReporter(Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            log.warn("Global purge timeout reached, skipping the internal (platform) reporter until next execution");
            return Completable.complete();
        }
        return auditReporterManager.getInternalReporter()
                .map(internalReporter -> {
                    log.debug("Purging audits for internal (platform) reporter");
                    return internalReporter.purgeExpiredData(deadline)
                            .onErrorResumeNext(error -> {
                                log.error("Failed to purge audits for internal (platform) reporter: {}", error.getMessage(), error);
                                return Completable.complete();
                            });
                })
                .orElseGet(() -> {
                    log.debug("No internal (platform) reporter to purge");
                    return Completable.complete();
                });
    }

    private Completable purgeReporter(io.gravitee.am.model.Reporter reporterConfig, Instant deadline) {
        if (!reporterConfig.isEnabled()) {
            return Completable.complete();
        }

        // do not start purging a new reporter once the global deadline has been reached
        if (Instant.now().isAfter(deadline)) {
            log.warn("Global purge timeout reached, skipping reporter {} until next execution", reporterConfig.getName());
            return Completable.complete();
        }

        return auditReporterManager.getReporter(reporterConfig.getReference())
                .flatMapCompletable(reporter -> {
                    log.debug("Purging audits for reporter: {}/{}", reporterConfig.getName(), reporterConfig.getId());
                    return reporter.purgeExpiredData(deadline);
                });
    }
}
