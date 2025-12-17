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

import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.common.ExpiredDataSweeperProvider;
import io.gravitee.am.repository.management.api.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Slf4j
public class ManagementExpiredDataSweeperProvider implements ExpiredDataSweeperProvider {

    @Lazy
    @Autowired
    protected EventRepository eventRepository;

    @Lazy
    @Autowired
    protected ReporterAuditSweeper reporterAuditSweeper;

    @Override
    public ExpiredDataSweeper getExpiredDataSweeper(ExpiredDataSweeper.Target target){
        return switch (target) {
            case events -> eventRepository;
            case audits -> reporterAuditSweeper;
            default -> {
                log.warn("Target {} is not supported by Management provider", target);
                yield null;
            }
        };
    }
}
