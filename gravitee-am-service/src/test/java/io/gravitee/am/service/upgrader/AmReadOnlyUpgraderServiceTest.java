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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.Upgrader;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmReadOnlyUpgraderServiceTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(AmReadOnlyUpgraderService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final UpgraderRepository repository = mock(UpgraderRepository.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void shouldWarnOnlyForUpgradersNotApplied() throws Exception {
        Upgrader applied = upgrader("applied_v1");
        Upgrader pending = upgrader("pending_v2");
        when(repository.findById("applied_v1")).thenReturn(Maybe.just(new UpgradeRecord("applied_v1", new Date())));
        when(repository.findById("pending_v2")).thenReturn(Maybe.empty());

        start(Map.of("applied", applied, "pending", pending));

        assertThat(warnings()).hasSize(1).first().asString().contains("id=pending_v2").doesNotContain("applied_v1");
    }

    @Test
    void shouldNeverApplyNorRecordAnyUpgrader() throws Exception {
        Upgrader pending = upgrader("pending_v2");
        when(repository.findById("pending_v2")).thenReturn(Maybe.empty());

        start(Map.of("pending", pending));

        verify(pending, never()).upgrade();
        verify(repository, never()).create(any());
    }

    @Test
    void shouldStartEvenWhenTheRecordsCannotBeRead() throws Exception {
        when(repository.findById("broken_v1")).thenReturn(Maybe.error(new IllegalStateException("unreachable")));

        start(Map.of("broken", upgrader("broken_v1")));

        assertThat(warnings()).singleElement().asString().contains("Unable to check whether");
    }

    private void start(Map<String, Object> beans) throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansWithAnnotation(ManagementRepositoryScope.class)).thenReturn(beans);
        AmReadOnlyUpgraderService service = new AmReadOnlyUpgraderService(repository, ManagementRepositoryScope.class);
        service.setApplicationContext(context);
        service.start();
    }

    private List<String> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Upgrader upgrader(String identifier) {
        Upgrader upgrader = mock(Upgrader.class);
        when(upgrader.identifier()).thenReturn(identifier);
        return upgrader;
    }
}
