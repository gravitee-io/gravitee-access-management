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
package io.gravitee.am.dataplane.api.upgrader;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataPlaneUpgraderServiceTest {

    private final UpgraderRepository repository = mock(UpgraderRepository.class);
    private final DataPlaneProvider dataPlane = mock(DataPlaneProvider.class);
    private final List<String> applied = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(dataPlane.getDataPlaneDescription()).thenReturn(new DataPlaneDescription("default", "Default", "mongo", "repositories.gateway", "http://gateway"));
        when(dataPlane.getUpgraderRepository()).thenReturn(repository);
        when(repository.findById(any())).thenReturn(Maybe.empty());
        when(repository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    }

    @Test
    void shouldApplyAndRecordTheUpgradersOfTheProvider() throws Exception {
        when(dataPlane.getUpgraders()).thenReturn(List.of(new VersionedUpgrader(applied)));

        new DataPlaneUpgraderService(false, () -> List.of(dataPlane)).start();

        ArgumentCaptor<UpgradeRecord> record = ArgumentCaptor.forClass(UpgradeRecord.class);
        verify(repository).create(record.capture());
        assertThat(applied).containsExactly("versioned");
        assertThat(record.getValue().getId()).isEqualTo("default_" + VersionedUpgrader.class.getName() + "_v1");
    }

    @Test
    void shouldSkipAProviderUpgraderAlreadyRecorded() throws Exception {
        String identifier = "default_" + VersionedUpgrader.class.getName() + "_v1";
        when(repository.findById(identifier)).thenReturn(Maybe.just(new UpgradeRecord(identifier, new Date())));
        when(dataPlane.getUpgraders()).thenReturn(List.of(new VersionedUpgrader(applied)));

        new DataPlaneUpgraderService(false, () -> List.of(dataPlane)).start();

        assertThat(applied).isEmpty();
        verify(repository, never()).create(any());
    }

    @Test
    void shouldNotRecordAFailedUpgraderAndKeepApplyingTheOthers() throws Exception {
        when(dataPlane.getUpgraders()).thenReturn(List.of(new FailingUpgrader(), new VersionedUpgrader(applied)));

        new DataPlaneUpgraderService(false, () -> List.of(dataPlane)).start();

        ArgumentCaptor<UpgradeRecord> record = ArgumentCaptor.forClass(UpgradeRecord.class);
        verify(repository).create(record.capture());
        assertThat(applied).containsExactly("versioned");
        assertThat(record.getValue().getId()).doesNotContain(FailingUpgrader.class.getName());
    }

    @Test
    void shouldKeepTheIdentifierOfAnUnversionedUpgrader() {
        DataPlaneDescription description = dataPlane.getDataPlaneDescription();

        assertThat(new FailingUpgrader().identifier(description)).isEqualTo("default_" + FailingUpgrader.class.getName());
    }

    private record VersionedUpgrader(List<String> applied) implements DataPlaneUpgrader {
        @Override
        public boolean upgrade(DataPlaneProvider dataPlaneProvider) {
            return applied.add("versioned");
        }

        @Override
        public String version() {
            return "v1";
        }
    }

    private static class FailingUpgrader implements DataPlaneUpgrader {
        @Override
        public boolean upgrade(DataPlaneProvider dataPlaneProvider) {
            throw new IllegalStateException("index build failed");
        }
    }
}
