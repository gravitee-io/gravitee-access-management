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
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.TaskType;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TasksLoaderTest {

    @InjectMocks
    private TasksLoader tasksLoader;

    @Mock
    private SystemTaskRepository taskRepository;

    @Mock
    private TaskScheduler scheduler;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSchedule_SimpleTask() throws Exception {
        var tasks = new ArrayList<>();
        final int numberOfSimpleTasks = new Random().nextInt(10);
        for (int i = 0; i < numberOfSimpleTasks; ++i) {
            var task = new SystemTask();
            task.setId("simple-task-"+i);
            task.setType(TaskType.SIMPLE.name());
            task.setKind(AssignSystemCertificate.class.getSimpleName());
            task.setConfiguration("{\"delay\": 1 , \"unit\": \"MINUTES\"}");
            tasks.add(task);
        }

        final int numberOfOtherTasks = new Random().nextInt(10);
        for (int i = 0; i < numberOfOtherTasks; ++i) {
            var task = new SystemTask();
            task.setId("other-task-"+i);
            task.setType(TaskType.SIMPLE.name());
            task.setKind("other");
            tasks.add(task);
        }

        doReturn(Flowable.fromIterable(tasks)).when(taskRepository).findByType(TaskType.SIMPLE.name());

        tasksLoader.doStart();

        verify(scheduler, times(numberOfSimpleTasks)).schedule(argThat(task -> task instanceof AssignSystemCertificate), any(Instant.class));
    }
}
