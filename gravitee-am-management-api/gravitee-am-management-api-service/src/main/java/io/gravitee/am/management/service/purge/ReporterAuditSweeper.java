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
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class ReporterAuditSweeper implements ExpiredDataSweeper {

    public static final String LEASE_ACTION_AUDIT_SWEEPER = "auditSweeper";
    public static final int MAX_CONCURRENCY = 4;
    public static final boolean NO_DELAY_ERRORS = false;
    public static final int DEFAULT_AUDIT_SWEEPER_LEASE_DURATION_IN_SECOND = 3600;

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

    @Value("${services.purge.audits.leaseDuration:"+ DEFAULT_AUDIT_SWEEPER_LEASE_DURATION_IN_SECOND +"}")
    private int leaseDuration = DEFAULT_AUDIT_SWEEPER_LEASE_DURATION_IN_SECOND;


    @Override
    public Completable purgeExpiredData() {
        log.info("Starting audit reporter data purge (retention: {} days)", retentionDays);

        return actionLeaseService.acquireLease(LEASE_ACTION_AUDIT_SWEEPER, Duration.of(leaseDuration, ChronoUnit.SECONDS))
                .flatMapPublisher(lease -> reporterService.findAll())
                .flatMapCompletable(reporter -> purgeReporter(reporter)
                        .onErrorResumeNext(error -> {
                            log.error("Failed to purge audits for reporter {}: {}",
                                    reporter.getName(), error.getMessage(), error);
                            return Completable.complete();
                        }),
                        NO_DELAY_ERRORS,
                        MAX_CONCURRENCY
                )
                .doOnComplete(() -> log.info("Audit reporter data purge completed"))
                .doOnError(error -> log.error("Audit reporter data purge failed", error));
    }

    private Completable purgeReporter(io.gravitee.am.model.Reporter reporterConfig) {
        if (!reporterConfig.isEnabled()) {
            return Completable.complete();
        }

        return auditReporterManager.getReporter(reporterConfig.getReference())
                .flatMapCompletable(reporter -> {
                    log.debug("Purging audits for reporter: {}", reporterConfig.getName());
                    return reporter.purgeExpiredData();
                });
    }
}
