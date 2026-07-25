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
package io.gravitee.am.gateway.handler.common.audit.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The end-user account activity screen reads audits through {@link GatewayAuditReporterManager#getReporter()},
 * so the same determinism the console needs applies here.
 *
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayAuditReporterManagerTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String ORGANIZATION_ID = "org-1";
    private static final String ENVIRONMENT_ID = "env-1";

    @Mock
    private ReporterRepository reporterRepository;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private Vertx vertx;

    private GatewayAuditReporterManager manager;

    @BeforeEach
    void setUp() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setId(ENVIRONMENT_ID);
        environment.setOrganizationId(ORGANIZATION_ID);
        when(environmentService.findById(ENVIRONMENT_ID)).thenReturn(Single.just(environment));

        manager = new GatewayAuditReporterManager();
        ReflectionTestUtils.setField(manager, "domain", domain);
        ReflectionTestUtils.setField(manager, "reporterRepository", reporterRepository);
        ReflectionTestUtils.setField(manager, "environmentService", environmentService);
        ReflectionTestUtils.setField(manager, "vertx", vertx);
        ReflectionTestUtils.setField(manager, "organizationId", ORGANIZATION_ID);
    }

    @Test
    void selectsTheReporterTheDomainHasHadLongest() {
        io.gravitee.am.model.Reporter database = model("zzz-database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("aaa-elasticsearch", daysAgo(1));
        Reporter databaseProvider = register(database, true);
        register(elasticsearch, true);

        assertThat(manager.getReporter()).isSameAs(databaseProvider);
    }

    @Test
    void skipsReportersThatCannotSearch() {
        io.gravitee.am.model.Reporter writeOnly = model("kafka", daysAgo(30));
        io.gravitee.am.model.Reporter searchable = model("elasticsearch", daysAgo(1));
        register(writeOnly, false);
        Reporter searchableProvider = register(searchable, true);

        assertThat(manager.getReporter()).isSameAs(searchableProvider);
    }

    @Test
    void aSingleReporterIsStillSelected() {
        io.gravitee.am.model.Reporter only = model("database", daysAgo(30));
        Reporter onlyProvider = register(only, true);

        assertThat(manager.getReporter()).isSameAs(onlyProvider);
    }

    @Test
    void disablingAReporterMovesReadsToTheNextOneWithoutARestart() {
        io.gravitee.am.model.Reporter database = model("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        register(database, true);
        Reporter elasticsearchProvider = register(elasticsearch, true);

        disable(database);

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> manager.getReporter() == elasticsearchProvider);
    }

    /**
     * Disabling the last searchable reporter must not leave reads with nothing to call. The end-user
     * account activity screen dereferences the result of {@link GatewayAuditReporterManager#getReporter()}
     * without a null check, so returning null turns a configuration choice into a 500. The management
     * side already degrades to an empty page in the same situation.
     */
    @Test
    void disablingTheLastReporterLeavesReadsAnsweringEmptyRatherThanNull() {
        io.gravitee.am.model.Reporter only = model("database", daysAgo(30));
        Reporter onlyProvider = register(only, true);

        disable(only);

        await().atMost(5, TimeUnit.SECONDS).until(() -> manager.getReporter() != onlyProvider);

        Reporter fallback = manager.getReporter();
        assertThat(fallback).isNotNull();
        assertThat(fallback.canSearch())
                .describedAs("the fallback exists to be callable, not to be selected as a real store")
                .isFalse();
        assertThat(((AuditReporter) fallback)
                .search(ReferenceType.DOMAIN, DOMAIN_ID, new AuditReportableCriteria.Builder().build(), 0, 10)
                .blockingGet()
                .getData())
                .isEmpty();
    }

    /**
     * Drives the manager's public event entry point with an updated, disabled reporter, exactly as the
     * reporter event bus would on a configuration change.
     */
    private void disable(io.gravitee.am.model.Reporter reporter) {
        io.gravitee.am.model.Reporter disabled = new io.gravitee.am.model.Reporter(reporter);
        disabled.setEnabled(false);
        disabled.setUpdatedAt(new Date());
        when(reporterRepository.findById(reporter.getId())).thenReturn(Maybe.just(disabled));

        manager.onEvent(new SimpleEvent<>(ReporterEvent.UPDATE,
                new Payload(reporter.getId(), Reference.domain(DOMAIN_ID), Action.UPDATE)));
    }

    private Reporter register(io.gravitee.am.model.Reporter model, boolean canSearch) {
        AuditReporter delegate = mock(AuditReporter.class);
        when(delegate.canSearch()).thenReturn(canSearch);
        Reporter provider = new EventBusReporterWrapper<>(vertx, delegate, Reference.domain(DOMAIN_ID));
        reporterPlugins().put(model.getId(), provider);
        deployedReporters().put(model.getId(), model);
        return provider;
    }

    private io.gravitee.am.model.Reporter model(String id, Date createdAt) {
        io.gravitee.am.model.Reporter reporter = new io.gravitee.am.model.Reporter();
        reporter.setId(id);
        reporter.setName(id);
        reporter.setType(id);
        reporter.setReference(Reference.domain(DOMAIN_ID));
        reporter.setEnabled(true);
        reporter.setCreatedAt(createdAt);
        reporter.setUpdatedAt(createdAt);
        return reporter;
    }

    private static Date daysAgo(int days) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Reporter> reporterPlugins() {
        return (Map<String, Reporter>) ReflectionTestUtils.getField(manager, "reporterPlugins");
    }

    @SuppressWarnings("unchecked")
    private Map<String, io.gravitee.am.model.Reporter> deployedReporters() {
        return (Map<String, io.gravitee.am.model.Reporter>) ReflectionTestUtils.getField(manager, "reporters");
    }
}
