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
package io.gravitee.am.service.upgrader;

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.api.upgrader.DataPlaneUpgrader;
import io.gravitee.am.dataplane.api.upgrader.DataPlaneUpgraderCheckService;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.Upgrader;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
public class UpgraderCheckServiceTest {

    @Test
    public void shouldLookUpTheRecordOfEveryUpgraderOfItsScopeWithoutApplyingAny() throws Exception {
        UpgraderRepository repository = mock(UpgraderRepository.class);
        Upgrader applied = upgrader("applied_v1");
        Upgrader pending = upgrader("pending_v2");
        when(repository.findById("applied_v1")).thenReturn(Maybe.just(new UpgradeRecord("applied_v1", new Date())));
        when(repository.findById("pending_v2")).thenReturn(Maybe.empty());

        AmUpgraderCheckService service = new AmUpgraderCheckService(repository, ManagementRepositoryScope.class);
        service.setApplicationContext(context(Map.of("applied", applied, "pending", pending)));
        service.start();

        verify(repository).findById("applied_v1");
        verify(repository).findById("pending_v2");
        verify(applied, never()).upgrade();
        verify(pending, never()).upgrade();
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldStartEvenWhenTheRecordsCannotBeRead() throws Exception {
        UpgraderRepository repository = mock(UpgraderRepository.class);
        when(repository.findById("broken_v1")).thenReturn(Maybe.error(new IllegalStateException("unreachable")));

        AmUpgraderCheckService service = new AmUpgraderCheckService(repository, ManagementRepositoryScope.class);
        service.setApplicationContext(context(Map.of("broken", upgrader("broken_v1"))));
        service.start();

        verify(repository).findById("broken_v1");
    }

    @Test
    public void shouldLookUpTheRecordOfEveryDataPlaneUpgraderWithoutApplyingAny() throws Exception {
        UpgraderRepository repository = mock(UpgraderRepository.class);
        when(repository.findById(any())).thenReturn(Maybe.empty());
        DataPlaneUpgrader upgrader = mock(DataPlaneUpgrader.class);
        when(upgrader.identifier(any())).thenReturn("default_indexes_v1");
        DataPlaneProvider dataPlane = mock(DataPlaneProvider.class);
        when(dataPlane.getDataPlaneDescription()).thenReturn(new DataPlaneDescription("default", "Default", "mongo", "repositories.gateway", "http://gateway"));
        when(dataPlane.getUpgraderRepository()).thenReturn(repository);
        when(dataPlane.getUpgraders()).thenReturn(List.of(upgrader));

        new DataPlaneUpgraderCheckService(() -> List.of(dataPlane)).start();

        verify(repository).findById("default_indexes_v1");
        verify(upgrader, never()).upgrade(any());
        verify(repository, never()).create(any());
    }

    private static Upgrader upgrader(String identifier) {
        Upgrader upgrader = mock(Upgrader.class);
        when(upgrader.identifier()).thenReturn(identifier);
        return upgrader;
    }

    private static ApplicationContext context(Map<String, Object> beans) {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansWithAnnotation(ManagementRepositoryScope.class)).thenReturn(beans);
        return context;
    }
}
