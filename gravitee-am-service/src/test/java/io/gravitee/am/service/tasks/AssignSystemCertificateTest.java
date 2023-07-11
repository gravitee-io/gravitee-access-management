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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.TaskManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class AssignSystemCertificateTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private TaskManager taskManager;

    @Test
    public void shouldNoProcessTask_MissingFromDB() {
        when(taskManager.isActiveTask(anyString())).thenReturn(Single.just(false));

        final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
        final var definition = new AssignSystemCertificateDefinition();
        definition.setUnit(TimeUnit.SECONDS);
        definition.setDelay(10);
        definition.setDeprecatedCertificate(UUID.randomUUID().toString());
        definition.setRenewedCertificate(UUID.randomUUID().toString());
        definition.setDomainId(UUID.randomUUID().toString());
        task.setDefinition(definition);

        task.run();

        verify(taskManager).isActiveTask(eq(task.getId()));
        verify(applicationService, never()).findByDomain(eq(definition.getDomainId()));
        verify(applicationService, never()).update(any());
        verify(taskManager, never()).remove(any());
    }

    @Test
    public void shouldNoProcessTask_ErrorOnApplicationSearch() {
        when(taskManager.isActiveTask(anyString())).thenReturn(Single.just(true));
        when(applicationService.findByDomain(anyString())).thenReturn(Single.error(new Exception()));
        when(certificateRepository.findById(anyString())).thenReturn(Maybe.just(new Certificate()));

        final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
        final var scheduler = mock(TaskScheduler.class);
        task.registerScheduler(scheduler);
        final var definition = new AssignSystemCertificateDefinition();
        definition.setUnit(TimeUnit.SECONDS);
        definition.setDelay(10);
        definition.setDeprecatedCertificate(UUID.randomUUID().toString());
        definition.setRenewedCertificate(UUID.randomUUID().toString());
        definition.setDomainId(UUID.randomUUID().toString());
        task.setDefinition(definition);

        task.run();

        verify(taskManager).isActiveTask(eq(task.getId()));
        verify(applicationService, never()).update(any());
        verify(taskManager, never()).remove(any());
        verify(scheduler).schedule(any(), any(Instant.class));
    }

    @Test
    public void shouldProcessTask() {
        final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
        final var scheduler = mock(TaskScheduler.class);
        task.registerScheduler(scheduler);
        final var definition = new AssignSystemCertificateDefinition();
        definition.setUnit(TimeUnit.SECONDS);
        definition.setDelay(10);
        definition.setDeprecatedCertificate(UUID.randomUUID().toString());
        definition.setRenewedCertificate(UUID.randomUUID().toString());
        definition.setDomainId(UUID.randomUUID().toString());
        task.setDefinition(definition);

        when(taskManager.isActiveTask(anyString())).thenReturn(Single.just(true));
        when(certificateRepository.findById(anyString())).thenReturn(Maybe.just(new Certificate()));

        var appToUpdate = new Application();
        appToUpdate.setId("appToUpdate");
        appToUpdate.setCertificate(definition.getDeprecatedCertificate());

        var appToIgnore = new Application();
        appToIgnore.setId("appToIgnore");
        appToIgnore.setCertificate(UUID.randomUUID().toString());

        var appToIgnoreNoCert = new Application();
        appToIgnoreNoCert.setId("appToIgnoreNoCert");
        appToIgnoreNoCert.setCertificate(null);

        when(applicationService.findByDomain(anyString())).thenReturn(Single.just(Set.of(appToUpdate, appToIgnore, appToIgnoreNoCert)));
        when(applicationService.update(any())).thenReturn(Single.just(appToUpdate));
        when(taskManager.remove(anyString())).thenReturn(Completable.complete());

        task.run();

        verify(taskManager).isActiveTask(eq(task.getId()));
        verify(applicationService).update(argThat(appli -> appli.getCertificate().equals(definition.getRenewedCertificate()) &&
                appli.getId().equals(appToUpdate.getId())));
        verify(applicationService, never()).update(argThat(appli -> !appli.getId().equals(appToUpdate.getId())));
        verify(taskManager).remove(anyString());
        verify(scheduler, never()).schedule(any(), any(Instant.class));
    }

    @Test
    public void shouldNotProcessTask_UnknownCertificate() {
        final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
        final var scheduler = mock(TaskScheduler.class);
        task.registerScheduler(scheduler);
        final var definition = new AssignSystemCertificateDefinition();
        definition.setUnit(TimeUnit.SECONDS);
        definition.setDelay(10);
        definition.setDeprecatedCertificate(UUID.randomUUID().toString());
        definition.setRenewedCertificate(UUID.randomUUID().toString());
        definition.setDomainId(UUID.randomUUID().toString());
        task.setDefinition(definition);

        when(taskManager.isActiveTask(anyString())).thenReturn(Single.just(true));
        when(certificateRepository.findById(anyString())).thenReturn(Maybe.empty());
        when(taskManager.remove(anyString())).thenReturn(Completable.complete());

        task.run();

        verify(taskManager).isActiveTask(eq(task.getId()));
        verify(applicationService, never()).update(any());
        verify(taskManager).remove(anyString());
        verify(scheduler, never()).schedule(any(), any(Instant.class));
    }
}
