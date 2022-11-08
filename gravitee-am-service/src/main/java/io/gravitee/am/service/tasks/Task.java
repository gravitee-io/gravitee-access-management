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

import org.springframework.scheduling.TaskScheduler;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Task<Def extends TaskDefinition> extends Runnable {

    String getId();

    TaskType type();

    Def getDefinition();
    default String kind() {
        return this.getClass().getSimpleName();
    }

    /**
     * @return true if the task have to be scheduled again on execution error
     */
    boolean rescheduledOnError();

    /**
     *
     * @param scheduler scheduler on which the task will be scheduled
     */
    void registerScheduler(TaskScheduler scheduler);

    /**
     * schedule the task using the TaskDefinition
     */
    void schedule();

}