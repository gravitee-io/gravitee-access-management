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

import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReporterAuditSweeper implements ExpiredDataSweeper {

    @Lazy
    private final AuditReporterManager auditReporterManager;

    @Lazy
    private final ReporterService reporterService;

    @Value("${services.purge.audits.retention.days:90}")
    private int retentionDays;

    @Override
    public Completable purgeExpiredData() {
        log.info("Starting audit reporter data purge (retention: {} days)", retentionDays);

        return reporterService.findAll()
                .flatMapCompletable(reporter -> purgeReporter(reporter)
                        .onErrorResumeNext(error -> {
                            log.error("Failed to purge audits for reporter {}: {}",
                                    reporter.getName(), error.getMessage(), error);
                            return Completable.complete();
                        })
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
