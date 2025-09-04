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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ScheduledPurgeServiceFactory {
    private final List<ExpiredDataSweeper.Target> supportedPurgeTargets;

    public ScheduledPurgeService createPurgeService(boolean enabled,
                                                    String cron,
                                                    List<String> excludedTargets,
                                                    TaskScheduler taskScheduler,
                                                    ExpiredDataSweepers sweepers) {
        List<ExpiredDataSweeper.Target> purgeTargets = purgeTargets(excludedTargets);
        return new ScheduledPurgeService(enabled, cron, taskScheduler, sweepers, purgeTargets);
    }

    private List<ExpiredDataSweeper.Target> purgeTargets(List<String> excluded) {
        try {
            List<ExpiredDataSweeper.Target> excludedPurgeTargets = excluded.stream().map(ExpiredDataSweeper.Target::valueOf).toList();
            return supportedPurgeTargets.stream().filter(target -> !excludedPurgeTargets.contains(target)).toList();
        } catch (Exception e){
            log.error("Error while resolving purge targets, fallback to default list", e);
            return supportedPurgeTargets;
        }
    }
}
