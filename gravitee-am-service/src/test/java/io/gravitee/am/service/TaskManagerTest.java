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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificateDefinition;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class TaskManagerTest {

    @InjectMocks
    private TaskManager taskManager;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private SystemTaskRepository taskRepository;

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private CertificateRepository certificateRepository;

    @Test
    public void shouldSchedule_task(){

        final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
        final var definition = new AssignSystemCertificateDefinition("domainid", "nexcertid", "deprecatedcertid");
        definition.setDelay(1);
        definition.setUnit(TimeUnit.HOURS);
        task.setDefinition(definition);

        when(taskRepository.create(any())).thenReturn(Single.just(new SystemTask()));

        taskManager.schedule(task);

        verify(taskRepository).create(argThat(sysTask -> sysTask.getKind().equals(task.kind()) &&
                sysTask.getId().equals(task.getId()) &&
                sysTask.getType().equals(task.type().name()) ));

        verify(scheduler).schedule(argThat(scheduledTask -> scheduledTask instanceof AssignSystemCertificate), any(Instant.class));
    }
}
