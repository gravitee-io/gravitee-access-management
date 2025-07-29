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
package io.gravitee.am.management.services.purge;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScheduledPurgeService extends AbstractService implements Runnable {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ScheduledPurgeService.class);

    @Autowired
    private TaskScheduler scheduler;

    @Value("${services.purge.cron:0 0 23 * * *}") // execute every day at 11 PM
    private String cronTrigger;

    @Value("${services.purge.enabled:true}")
    private boolean enabled;

    @Value("${services.purge.exclude:}")
    private String exclude;

    private final AtomicLong counter = new AtomicLong(0);

    @Autowired
    @Lazy
    private PurgeManager purgeManager;

    @Autowired
    private RepositoriesEnvironment environment;

    @Override
    protected void doStart() throws Exception {
        String type = environment.getProperty("repositories.management.type", "mongodb");
        if (enabled && "jdbc".equalsIgnoreCase(type)) { // even if enabled, useless for Mongo implementation
            super.doStart();
            logger.info("Purge service has been initialized with cron [{}]", cronTrigger);
            // Sync must start only when doStart() is invoked, that's the reason why we are not
            // using @Scheduled annotation on doSync() method.
            scheduler.schedule(this, new CronTrigger(cronTrigger));
        } else {
            logger.info("Purge service has been disabled, enabled={}, type={}", enabled, type);
        }
    }

    @Override
    public void run() {
        doPurgeExpiredData();
    }

    /**
     * Cleaning expired data from Relational Database when Gravitee node is starting.
     * This cleaning phase can be done by all nodes or only one depending on the enabled flag.
     */
    private void doPurgeExpiredData() {
        logger.debug("Cleaning expired data #{} started at {}", counter.incrementAndGet(), Instant.now().toString());
        this.purgeManager.purge(Arrays.stream(exclude.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(value -> TableName.getValueOf(value).stream())
                .collect(Collectors.toList()));
        logger.debug("Cleaning expired data #{} ended at {}", counter.get(), Instant.now().toString());
    }

    @Override
    protected String name() {
        return "Purge Service";
    }
}
