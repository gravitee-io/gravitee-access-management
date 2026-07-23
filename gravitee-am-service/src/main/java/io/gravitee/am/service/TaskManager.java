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
package io.gravitee.am.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.tasks.Task;
import io.gravitee.am.service.tasks.TaskDefinition;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Date;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class TaskManager {


    @Autowired
    private ObjectMapper mapper;
    @Autowired
    @Lazy
    private SystemTaskRepository taskRepository;

    @Autowired
    private TaskScheduler scheduler;

    public void schedule(Task<? extends TaskDefinition> task) {
        log.debug("schedule {} task of type {}", task.type(), task.kind());

        try {
            final var systemTask = new SystemTask();
            systemTask.setId(task.getId());
            systemTask.setCreatedAt(new Date());
            systemTask.setUpdatedAt(systemTask.getCreatedAt());
            systemTask.setType(task.type().name());
            systemTask.setKind(task.kind());
            systemTask.setStatus(SystemTaskStatus.INITIALIZED.name());
            systemTask.setOperationId(task.kind() + "-" + systemTask.getId());
            systemTask.setConfiguration(mapper.writeValueAsString(task.getDefinition()));

            // persist the task to be able to reload it
            // if the management restart before the task execution
            this.taskRepository.create(systemTask)
                    .subscribe(
                            createdTask -> log.debug("Task {} of type {} persisted", createdTask.getId(), task.kind()),
                            error -> log.warn("Task of type {} can't be persisted", task.kind(), error)
                            );

            task.registerScheduler(this.scheduler);
            task.schedule();
            log.debug("{} task of type {} with id {} scheduled: {}", task.type(), task.kind(), systemTask.getId(), task.getDefinition());

        } catch (JsonProcessingException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to schedule the task {} with definition {} due to: ", task.getId(), task.getDefinition(), e);
            } else {
                log.error("Unable to schedule the task {} with definition {} due to : {}", task.getId(), task.getDefinition(), e.getMessage());
            }
        }
    }

    public Single<Boolean> isActiveTask(String taskId) {
        return this.taskRepository.findById(taskId)
                .isEmpty()
                // as isEmpty return Single<True> if the task doesn't exist,
                // we have to apply a negation be able to return true if Maybe contains something
                .map(value -> !value);
    }

    public Completable remove(String taskId) {
        return this.taskRepository.delete(taskId);
    }

    public Completable markAsError(String taskId) {
        return this.taskRepository.findById(taskId)
                .flatMapSingle(task -> {
                    task.setStatus(SystemTaskStatus.FAILURE.name());
                    return this.taskRepository.updateIf(task, task.getOperationId());
                }).ignoreElement();
    }
}
