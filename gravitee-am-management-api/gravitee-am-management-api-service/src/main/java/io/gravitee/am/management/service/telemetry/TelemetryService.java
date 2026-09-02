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
package io.gravitee.am.management.service.telemetry;

import io.gravitee.am.service.InstallationService;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.cluster.ClusterManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Schedules the daily usage summary and the weekly domain pass.
 * <p>
 * Only the primary cluster member reports, so a clustered installation sends one set of reports. A
 * failure is logged at DEBUG and dropped: there is no retry loop, and an installation with no route
 * to the collector stays silent.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class TelemetryService extends AbstractService<TelemetryService> {

    private final TelemetrySettings settings;
    private final TaskScheduler scheduler;
    private final ClusterManager clusterManager;
    private final InstallationService installationService;
    private final SummaryReportCollector summaryCollector;
    private final DomainPassRunner domainPassRunner;
    private final TelemetryPublisher publisher;

    /** One run at a time. The summary and the domain pass never overlap. */
    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!settings.enabled()) {
            log.info("Telemetry is disabled.");
            return;
        }
        log.info(
            "Telemetry is enabled. A daily usage summary and a weekly domain report go to {}. " +
            "Preview them at GET /_node/telemetry. Set telemetry.enabled=false to turn it off.",
            settings.endpoint()
        );
        scheduler.schedule(this::sendSummary, Instant.now().plusMillis(settings.initialDelay()));
        scheduler.schedule(this::sendSummary, new CronTrigger(settings.summaryCron()));
        if (settings.domainsEnabled()) {
            scheduler.schedule(this::runDomainPass, new CronTrigger(settings.domainsCron()));
        }
    }

    private void sendSummary() {
        run("summary", summaryCollector.collect().flatMapCompletable(report -> publisher.send(settings.reportsUrl(), report)));
    }

    private void runDomainPass() {
        run("domain pass", installationId().flatMapCompletable(domainPassRunner::run));
    }

    private Single<String> installationId() {
        return installationService.get().map(io.gravitee.am.model.Installation::getId);
    }

    /**
     * Applies the three guards every run shares: the node must be primary, no other run may hold
     * the lock, and the spread delay must elapse first.
     */
    private void run(String what, Completable work) {
        if (!clusterManager.self().primary()) {
            log.debug("Skipping the telemetry {}: this node is not the primary cluster member", what);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping the telemetry {}: another telemetry run is in progress", what);
            return;
        }
        // The spread delay keeps the world's installations from reporting in the same second.
        final int spread = settings.spreadMinutes() > 0 ? ThreadLocalRandom.current().nextInt(settings.spreadMinutes()) : 0;
        scheduler.schedule(
            () ->
                work.subscribe(
                    () -> {
                        running.set(false);
                        log.debug("The telemetry {} completed", what);
                    },
                    throwable -> {
                        running.set(false);
                        log.debug("The telemetry {} failed. The next attempt waits for the next tick.", what, throwable);
                    }
                ),
            Instant.now().plus(spread, ChronoUnit.MINUTES)
        );
    }

    @Override
    protected String name() {
        return "Telemetry Service";
    }
}
