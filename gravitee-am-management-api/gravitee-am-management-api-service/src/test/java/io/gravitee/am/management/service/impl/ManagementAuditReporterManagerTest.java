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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.provider.NoOpReporter;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Audit reads resolve to a single reporter, so the winner has to be defined rather than left to map
 * iteration order. That non-determinism is exactly what an integration test would pass by luck, so it
 * is pinned down here.
 *
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagementAuditReporterManagerTest {

    private static final Reference DOMAIN = Reference.domain("domain-1");

    @Mock
    private ReporterService reporterService;

    @Mock
    private Vertx vertx;

    private ManagementAuditReporterManager manager;

    @BeforeEach
    void setUp() {
        manager = new ManagementAuditReporterManager();
        ReflectionTestUtils.setField(manager, "reporterService", reporterService);
        ReflectionTestUtils.setField(manager, "vertx", vertx);
        // normally built in doStart(); it is what the manager falls back to when nothing can search
        ReflectionTestUtils.setField(manager, "noOpReporter", new NoOpReporter());
    }

    @Test
    void selectsTheReporterTheReferenceHasHadLongest() {
        io.gravitee.am.model.Reporter database = model("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        Reporter databaseProvider = register(database, true);
        register(elasticsearch, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(databaseProvider);
    }

    @Test
    void selectionIgnoresReporterIdOrdering() {
        // ids deliberately ordered the opposite way round to creation, so an id-ordered or
        // arbitrarily-ordered lookup would pick the elasticsearch reporter instead
        io.gravitee.am.model.Reporter database = model("zzz-database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("aaa-elasticsearch", daysAgo(1));
        Reporter databaseProvider = register(database, true);
        register(elasticsearch, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(databaseProvider);
    }

    @Test
    void skipsReportersThatCannotSearch() {
        io.gravitee.am.model.Reporter writeOnly = model("kafka", daysAgo(30));
        io.gravitee.am.model.Reporter searchable = model("elasticsearch", daysAgo(1));
        register(writeOnly, false);
        Reporter searchableProvider = register(searchable, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(searchableProvider);
    }

    @Test
    void aSingleReporterIsStillSelected() {
        io.gravitee.am.model.Reporter only = model("database", daysAgo(30));
        Reporter onlyProvider = register(only, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(onlyProvider);
    }

    @Test
    void disablingAReporterMovesReadsToTheNextOneWithoutARestart() {
        io.gravitee.am.model.Reporter database = model("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        register(database, true);
        Reporter elasticsearchProvider = register(elasticsearch, true);

        disable(database);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(elasticsearchProvider);
    }

    @Test
    void aDisabledReporterNoLongerAnswersAuditQueries() {
        io.gravitee.am.model.Reporter only = model("database", daysAgo(30));
        Reporter disabledProvider = register(only, true);
        when(reporterService.findByReference(DOMAIN)).thenReturn(Flowable.just(only));

        disable(only);

        // nothing searchable is left, so the manager falls back to the not-bootstrapped-yet reporter
        // rather than continuing to serve reads from the disabled one
        Reporter selected = manager.getReporter(DOMAIN).blockingGet();
        assertThat(selected).isNotSameAs(disabledProvider);
        assertThat(selected.canSearch()).isFalse();
    }

    /**
     * Drives the manager's public event entry point with an updated, disabled reporter, exactly as the
     * reporter event bus would on a configuration change.
     */
    private void disable(io.gravitee.am.model.Reporter reporter) {
        io.gravitee.am.model.Reporter disabled = new io.gravitee.am.model.Reporter(reporter);
        disabled.setEnabled(false);
        disabled.setUpdatedAt(new Date());
        when(reporterService.findById(reporter.getId())).thenReturn(Maybe.just(disabled));

        manager.onEvent(new SimpleEvent<>(ReporterEvent.UPDATE, new Payload(reporter.getId(), DOMAIN, Action.UPDATE)));
    }

    private Reporter register(io.gravitee.am.model.Reporter model, boolean canSearch) {
        AuditReporter delegate = mock(AuditReporter.class);
        when(delegate.canSearch()).thenReturn(canSearch);
        Reporter provider = new EventBusReporterWrapper<>(vertx, delegate, DOMAIN);
        auditReporters().put(model, provider);
        deployedReporters().put(model.getId(), model);
        return provider;
    }

    private io.gravitee.am.model.Reporter model(String id, Date createdAt) {
        io.gravitee.am.model.Reporter reporter = new io.gravitee.am.model.Reporter();
        reporter.setId(id);
        reporter.setName(id);
        reporter.setType(id);
        reporter.setReference(DOMAIN);
        reporter.setEnabled(true);
        reporter.setCreatedAt(createdAt);
        reporter.setUpdatedAt(createdAt);
        return reporter;
    }

    private static Date daysAgo(int days) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<io.gravitee.am.model.Reporter, Reporter> auditReporters() {
        return (ConcurrentMap<io.gravitee.am.model.Reporter, Reporter>) ReflectionTestUtils.getField(manager, "auditReporters");
    }

    @SuppressWarnings("unchecked")
    private Map<String, io.gravitee.am.model.Reporter> deployedReporters() {
        return (Map<String, io.gravitee.am.model.Reporter>) ReflectionTestUtils.getField(manager, "reporters");
    }
}
