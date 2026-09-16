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
package io.gravitee.am.management.service.impl.upgrades.system.upgraders;

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.impl.upgrades.DomainTrustedIssuerUpgrader;
import io.gravitee.am.management.service.trustdomain.TrustedIssuerProjection;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainTrustedIssuerMirrorUpgraderTest {

    @Mock
    private DomainService domainService;

    @Mock
    private TrustedIssuerProjection trustedIssuerProjection;

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @InjectMocks
    private DomainTrustedIssuerMirrorUpgrader upgrader;

    @Test
    void shouldSyncEveryDomainWithTokenExchangeSettingsOnceTheMigrationSucceeded() {
        migrationTask(SystemTaskStatus.SUCCESS);
        when(domainService.listAll()).thenReturn(Flowable.just(domain("with-settings", true), domain("without-settings", false)));
        when(trustedIssuerProjection.syncStored("with-settings")).thenReturn(Completable.complete());

        upgrader.upgrade().test().assertComplete();

        verify(trustedIssuerProjection).syncStored("with-settings");
        verify(trustedIssuerProjection, never()).syncStored("without-settings");
    }

    @Test
    void shouldSkipWhileTheMigrationIsOngoing() {
        migrationTask(SystemTaskStatus.ONGOING);

        upgrader.upgrade().test().assertComplete();

        verify(domainService, never()).listAll();
        verify(trustedIssuerProjection, never()).syncStored(any());
    }

    @Test
    void shouldSkipBeforeTheMigrationHasRun() {
        when(systemTaskRepository.findById(DomainTrustedIssuerUpgrader.TASK_ID)).thenReturn(Maybe.empty());

        upgrader.upgrade().test().assertComplete();

        verify(domainService, never()).listAll();
    }

    private void migrationTask(SystemTaskStatus status) {
        SystemTask task = new SystemTask();
        task.setStatus(status.name());
        when(systemTaskRepository.findById(DomainTrustedIssuerUpgrader.TASK_ID)).thenReturn(Maybe.just(task));
    }

    private static Domain domain(String id, boolean withTokenExchangeSettings) {
        Domain domain = new Domain();
        domain.setId(id);
        domain.setTokenExchangeSettings(withTokenExchangeSettings ? new TokenExchangeSettings() : null);
        return domain;
    }
}
