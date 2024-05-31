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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.DomainService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class SystemCertificateUpgraderTest {

    public static final String DOMAIN_2 = "DOMAIN_2";
    public static final String DOMAIN_1 = "DOMAIN_1";

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private DomainService domainService;

    @Mock
    private CertificateRepository certificateRepository;

    @InjectMocks
    private SystemCertificateUpgrader upgrader;

    @Test
    public void shouldIgnore_IfTaskCompleted() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(domainService, never()).findAll();
        verify(certificateRepository, never()).findByDomain(anyString());
    }

    @Test
    public void shouldUpgrade() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime twoYearsOld = now.minusYears(2);

        final Domain domain1 = new Domain();
        domain1.setId(DOMAIN_1);
        domain1.setCreatedAt(new Date(twoYearsOld.toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate defaultDomain1 = new Certificate();
        defaultDomain1.setName("Default");
        defaultDomain1.setCreatedAt(new Date(twoYearsOld.plusSeconds(30).toInstant(ZoneOffset.UTC).toEpochMilli()));
        defaultDomain1.setUpdatedAt(new Date(twoYearsOld.plusSeconds(40).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateRepository.findByDomain(eq(DOMAIN_1))).thenReturn(Flowable.just(defaultDomain1));

        final LocalDateTime oneYearOld = now.minusYears(1);
        final Domain domain2 = new Domain();
        domain2.setId(DOMAIN_2);
        domain2.setCreatedAt(new Date(oneYearOld.toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate defaultDomain2 = new Certificate();
        defaultDomain2.setName("Default");
        defaultDomain2.setCreatedAt(new Date(oneYearOld.plusSeconds(30).toInstant(ZoneOffset.UTC).toEpochMilli()));
        defaultDomain2.setUpdatedAt(new Date(oneYearOld.plusSeconds(40).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate notDefaultDomain2 = new Certificate();
        notDefaultDomain2.setName("Default");
        notDefaultDomain2.setCreatedAt(new Date(oneYearOld.plusSeconds(70).toInstant(ZoneOffset.UTC).toEpochMilli()));
        notDefaultDomain2.setUpdatedAt(new Date(oneYearOld.plusSeconds(75).toInstant(ZoneOffset.UTC).toEpochMilli()));
        when(certificateRepository.findByDomain(eq(DOMAIN_2))).thenReturn(Flowable.just(defaultDomain2, notDefaultDomain2));

        when(certificateRepository.update(any())).thenReturn(Single.just(new Certificate()));

        when(domainService.listAll()).thenReturn(Flowable.just(domain1, domain2));

        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer((args) -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(anyString());

        verify(certificateRepository, times(2)).update(argThat(Certificate::isSystem));
        verify(certificateRepository, never()).update(argThat(cert -> !cert.isSystem()));

        verify(systemTaskRepository, times(2)).updateIf(any(), any());
    }

    @Test
    public void shouldUpgradeOngoing() {
        String id = UUID.randomUUID().toString();

        SystemTask ongoingTask = new SystemTask();
        ongoingTask.setOperationId(id);
        ongoingTask.setId(id);
        ongoingTask.setStatus(SystemTaskStatus.ONGOING.name());

        SystemTask finalizedTask = new SystemTask();
        finalizedTask.setOperationId(id);
        finalizedTask.setId(id);
        finalizedTask.setStatus(SystemTaskStatus.SUCCESS.name());

        // first call no task, then ongoing and finally the successful one
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.empty(), Maybe.just(ongoingTask), Maybe.just(finalizedTask));
        when(systemTaskRepository.create(any())).thenReturn(Single.error(new Exception()));

        upgrader.upgrade();

        verify(systemTaskRepository, times(3)).findById(anyString());
        verify(domainService, never()).findAll();
        verify(certificateRepository, never()).findByDomain(anyString());

        verify(systemTaskRepository, never()).updateIf(argThat( t -> t.getStatus().equalsIgnoreCase(SystemTaskStatus.SUCCESS.name())), anyString());
    }
}
