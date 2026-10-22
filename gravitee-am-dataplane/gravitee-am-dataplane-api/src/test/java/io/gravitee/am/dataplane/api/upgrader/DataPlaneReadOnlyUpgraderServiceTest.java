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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataPlaneReadOnlyUpgraderServiceTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(DataPlaneReadOnlyUpgraderService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final UpgraderRepository repository = mock(UpgraderRepository.class);
    private final DataPlaneProvider dataPlane = mock(DataPlaneProvider.class);

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        when(dataPlane.getDataPlaneDescription()).thenReturn(new DataPlaneDescription("default", "Default", "mongo", "repositories.gateway", "http://gateway"));
        when(dataPlane.getUpgraderRepository()).thenReturn(repository);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void shouldWarnOnlyForDataPlaneUpgradersNotApplied() throws Exception {
        DataPlaneUpgrader applied = upgrader("default_applied_v1");
        DataPlaneUpgrader pending = upgrader("default_pending_v2");
        when(repository.findById("default_applied_v1")).thenReturn(Maybe.just(new UpgradeRecord("default_applied_v1", new Date())));
        when(repository.findById("default_pending_v2")).thenReturn(Maybe.empty());
        when(dataPlane.getUpgraders()).thenReturn(List.of(applied, pending));

        new DataPlaneReadOnlyUpgraderService(() -> List.of(dataPlane)).start();

        assertThat(warnings()).hasSize(1).first().asString().contains("id=default_pending_v2").doesNotContain("default_applied_v1");
        verify(applied, never()).upgrade(any());
        verify(pending, never()).upgrade(any());
        verify(repository, never()).create(any());
    }

    private List<String> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static DataPlaneUpgrader upgrader(String identifier) {
        DataPlaneUpgrader upgrader = mock(DataPlaneUpgrader.class);
        when(upgrader.identifier(any())).thenReturn(identifier);
        return upgrader;
    }
}
