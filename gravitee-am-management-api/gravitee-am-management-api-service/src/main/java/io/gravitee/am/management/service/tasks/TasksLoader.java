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
package io.gravitee.am.management.service.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.TaskManager;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificateDefinition;
import io.gravitee.am.service.tasks.TaskType;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * This class is used to read tasks from the SystemTask that may be scheduled.
 * The goal is to ensure that scheduled task are executed if the Management instance
 * restart before the task execution.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class TasksLoader extends AbstractService<TasksLoader> implements LifecycleComponent<TasksLoader> {

    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private TaskScheduler scheduler;
    @Autowired
    @Lazy
    private SystemTaskRepository taskRepository;
    @Autowired
    @Lazy
    private ApplicationService applicationService;
    @Autowired
    @Lazy
    private CertificateRepository certificateRepository;
    @Autowired
    private TaskManager taskManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Load scheduled tasks");
        this.taskRepository.findByType(TaskType.SIMPLE.name())
                // currently only one kind of Simple tasks, so we simply filter on this value for safety
                .filter(systemTask -> AssignSystemCertificate.class.getSimpleName().equals(systemTask.getKind()))
                .map(systemTask -> {
                    final var assignSystemCert = new AssignSystemCertificate(systemTask.getId(), this.applicationService, this.certificateRepository, this.taskManager);
                    final var taskConfiguration = mapper.readValue(systemTask.getConfiguration(), AssignSystemCertificateDefinition.class);
                    assignSystemCert.setDefinition(taskConfiguration);
                    return assignSystemCert;
                })
                .subscribe(task -> {
                    log.debug("Reschedule {} task of type {} with definition {}", task.type(), task.kind(), task.getDefinition());
                    task.registerScheduler(this.scheduler);
                    task.schedule();
                });
    }
}
