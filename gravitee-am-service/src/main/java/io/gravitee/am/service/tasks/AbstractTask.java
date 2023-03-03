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
package io.gravitee.am.service.tasks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractTask<Def extends TaskDefinition> implements Task {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @JsonIgnore
    private TaskScheduler scheduler;

    private String id;

    protected AbstractTask(String id) {
        this.id = id;
    }

    @Override
    public void registerScheduler(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public final void schedule() {
        if (this.scheduler != null) {
            var def = this.getDefinition();
            this.scheduler.schedule(this, Instant.now().plus(def.getDelay(), def.getUnit().toChronoUnit()));
        } else {
            logger.warn("Trying to schedule a {} task before the registration of the TaskScheduler", this.kind());
        }
    }

    @Override
    public String getId() {
        return id;
    }
}
