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
package io.gravitee.am.service.purge;

import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.common.ExpiredDataSweeper.Target;
import io.gravitee.am.repository.common.ExpiredDataSweeperProvider;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
@Slf4j
public class ScheduledPurgeService extends AbstractService implements Runnable {
    private final AtomicLong counter = new AtomicLong(0);

    private final boolean enabled;
    private final String cronTrigger;
    private final TaskScheduler scheduler;
    private final ExpiredDataSweeperProvider sweeper;
    private final List<Target> purgeTargets;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if(enabled) {
            log.info("Purge service has been initialized with cron [{}]", cronTrigger);
            // Sync must start only when doStart() is invoked, that's the reason why we are not
            // using @Scheduled annotation on doSync() method.
            scheduler.schedule(this, new CronTrigger(cronTrigger));
        } else {
            log.info("Purge service has been disabled");
        }
    }

    @Override
    public void run() {
        doPurgeExpiredData();
    }

    private void doPurgeExpiredData() {
        log.debug("Cleaning expired data #{} started at {}", counter.incrementAndGet(), Instant.now().toString());
        Completable
                .concat(getJobs())
                .subscribe();
        log.debug("Cleaning expired data #{} ended at {}", counter.get(), Instant.now().toString());
    }

    private List<Completable> getJobs(){
        return this.purgeTargets.stream()
                .flatMap(target -> getSweeper(target).stream())
                .map(ExpiredDataSweeper::purgeExpiredData)
                .toList();
    }

    private Optional<ExpiredDataSweeper> getSweeper(Target target) {
        try {
            return Optional.ofNullable(sweeper.getExpiredDataSweeper(target));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    protected String name() {
        return "Purge Service";
    }
}
