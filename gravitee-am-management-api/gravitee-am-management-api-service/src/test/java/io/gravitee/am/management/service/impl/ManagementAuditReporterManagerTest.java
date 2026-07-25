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
import io.gravitee.am.model.ReporterStatus;
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
import java.util.List;
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
    private static final Reference ORGANIZATION = Reference.organization("org-1");

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
    void anAddedReporterOutranksTheOneProvisionedWithTheDomain() {
        // the database reporter is created with the domain, so it is always the older of the two;
        // without the system tie-break an administrator could never move reads onto Elasticsearch
        io.gravitee.am.model.Reporter autoProvisioned = autoProvisioned("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        register(autoProvisioned, true);
        Reporter elasticsearchProvider = register(elasticsearch, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(elasticsearchProvider);
    }

    @Test
    void theProvisionedReporterStillServesReadsWhenNothingElseWasAdded() {
        io.gravitee.am.model.Reporter autoProvisioned = autoProvisioned("database", daysAgo(30));
        Reporter provider = register(autoProvisioned, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(provider);
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
    void aReporterThatFailedToStartDoesNotWinReads() {
        // the failure mode this exists for: an Elasticsearch reporter added with a configuration its
        // cluster refuses outranks the database reporter, and would answer every query with nothing
        // while the history sits in the database, reachable
        io.gravitee.am.model.Reporter database = autoProvisioned("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        Reporter databaseProvider = register(database, true);
        register(elasticsearch, true, ReporterStatus.FAILED);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(databaseProvider);
    }

    @Test
    void aReporterThatHasNotFinishedStartingStillWinsReads() {
        // a slow start is not a failure. Falling back to the database here would resurrect the problem
        // added-reporter precedence solves: during the migration window Elasticsearch is meant to look
        // empty, not to divert reads to the store being migrated away from
        io.gravitee.am.model.Reporter database = autoProvisioned("database", daysAgo(30));
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        register(database, true);
        Reporter startingProvider = register(elasticsearch, true, ReporterStatus.STARTING);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(startingProvider);
    }

    @Test
    void reportsTheStatusOfADeployedReporter() {
        io.gravitee.am.model.Reporter elasticsearch = model("elasticsearch", daysAgo(1));
        register(elasticsearch, true, ReporterStatus.FAILED);

        assertThat(manager.getStatus("elasticsearch")).isEqualTo(ReporterStatus.FAILED);
    }

    @Test
    void reportsAReporterThisNodeHasNotLoadedAsStarting() {
        assertThat(manager.getStatus("never-heard-of-it")).isEqualTo(ReporterStatus.STARTING);
    }

    @Test
    void anInheritedOrganizationReporterServesTheDomainsReads() {
        // it already receives this domain's audits; before this it could never answer for them, so the
        // recommended shape at scale — one inherited organization reporter — was write-only
        io.gravitee.am.model.Reporter database = autoProvisioned("database", daysAgo(30));
        io.gravitee.am.model.Reporter organisationWide = model("org-elasticsearch", daysAgo(1));
        organisationWide.setReference(ORGANIZATION);
        organisationWide.setInherited(true);
        register(database, true);
        Reporter inheritedProvider = registerInherited(organisationWide, DOMAIN);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(inheritedProvider);
    }

    @Test
    void anOrganizationReporterThatIsNotInheritedNeverServesADomain() {
        io.gravitee.am.model.Reporter database = autoProvisioned("database", daysAgo(30));
        io.gravitee.am.model.Reporter organisationOnly = model("org-elasticsearch", daysAgo(1));
        organisationOnly.setReference(ORGANIZATION);
        Reporter databaseProvider = register(database, true);
        registerFor(organisationOnly, ORGANIZATION);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(databaseProvider);
    }

    @Test
    void aDomainsOwnReporterOutranksAnInheritedOne() {
        io.gravitee.am.model.Reporter organisationWide = model("org-elasticsearch", daysAgo(30));
        organisationWide.setReference(ORGANIZATION);
        organisationWide.setInherited(true);
        io.gravitee.am.model.Reporter own = model("domain-elasticsearch", daysAgo(1));
        registerInherited(organisationWide, DOMAIN);
        Reporter ownProvider = register(own, true);

        assertThat(manager.getReporter(DOMAIN).blockingGet()).isSameAs(ownProvider);
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
        return register(model, canSearch, ReporterStatus.READY);
    }

    private Reporter register(io.gravitee.am.model.Reporter model, boolean canSearch, ReporterStatus status) {
        return registerWith(model, canSearch, status, new Reference[]{DOMAIN});
    }

    /** An organization reporter that consumes only its own reference, as a non-inherited one does. */
    private Reporter registerFor(io.gravitee.am.model.Reporter model, Reference reference) {
        return registerWith(model, true, ReporterStatus.READY, new Reference[]{reference});
    }

    /**
     * An inherited organization reporter, which the launcher wires to its organization <em>and</em>
     * every domain in it — the write-side fact read selection now consults.
     */
    private Reporter registerInherited(io.gravitee.am.model.Reporter model, Reference... domains) {
        Reference[] references = new Reference[domains.length + 1];
        references[0] = model.getReference();
        System.arraycopy(domains, 0, references, 1, domains.length);
        return registerWith(model, true, ReporterStatus.READY, references);
    }

    private Reporter registerWith(io.gravitee.am.model.Reporter model, boolean canSearch, ReporterStatus status, Reference[] references) {
        AuditReporter delegate = mock(AuditReporter.class);
        when(delegate.canSearch()).thenReturn(canSearch);
        when(delegate.status()).thenReturn(status);
        Reporter provider = new EventBusReporterWrapper<>(vertx, delegate, List.of(references));
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

    private io.gravitee.am.model.Reporter autoProvisioned(String id, Date createdAt) {
        io.gravitee.am.model.Reporter reporter = model(id, createdAt);
        reporter.setSystem(true);
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
