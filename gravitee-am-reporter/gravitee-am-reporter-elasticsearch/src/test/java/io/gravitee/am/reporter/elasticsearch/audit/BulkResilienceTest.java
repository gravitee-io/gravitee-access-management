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
package io.gravitee.am.reporter.elasticsearch.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestContainer;
import io.gravitee.am.reporter.elasticsearch.NetworkGate;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * What the reporter does when Elasticsearch misbehaves: a refused document, an outage, and a
 * shutdown mid-flight. All three used to lose audits silently.
 *
 * @author GraviteeSource Team
 */
class BulkResilienceTest {

    private static final Instant DAY = Instant.parse("2026-07-25T10:00:00Z");
    private static final String DAILY_SUFFIX = "-2026.07.25";

    private static ElasticsearchTestClient elasticsearch;
    private static String elasticsearchHost;
    private static int elasticsearchPort;

    private final List<String> indexPatterns = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeAll
    static void connect() {
        elasticsearch = ElasticsearchTestClient.onTestContainer();
        URI endpoint = URI.create(ElasticsearchTestContainer.endpoint());
        elasticsearchHost = endpoint.getHost();
        elasticsearchPort = endpoint.getPort();
    }

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logs.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach
    void releaseLogsAndIndices() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        logs.stop();
        indexPatterns.forEach(pattern -> elasticsearch.cleanUp(pattern));
        indexPatterns.clear();
    }

    @Test
    void oneRefusedDocumentDoesNotTakeTheRestOfItsBatchDownWithIt() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        try (ReporterHarness harness = ReporterHarness.start(ReporterHarness.configurationFor(index))) {
            elasticsearch.awaitTemplate(AuditIndexTemplate.name(index));
            // created after the template, so it inherits it and only overrides one field: outcome.message
            // carries a string on every failed audit, so mapping it as a long makes exactly the failed
            // audit in this batch unparseable while the successful ones index normally
            elasticsearch.put("/" + index + DAILY_SUFFIX, """
                    {"mappings":{"properties":{"outcome":{"properties":{"message":{"type":"long"}}}}}}""");
            elasticsearch.awaitSearchable(index + "-*");

            Audit refused = AuditFixtures.audit(domain, "USER_LOGIN", Status.FAILURE, DAY);
            harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, DAY));
            harness.reporter().report(refused);
            harness.reporter().report(AuditFixtures.audit(domain, "USER_UPDATED", Status.SUCCESS, DAY));

            await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).ignoreExceptions()
                    .until(() -> search(harness, domain).getData().size() == 2);

            assertThat(search(harness, domain).getData()).extracting(Audit::getType)
                    .describedAs("the other audits in the batch must survive one refused document")
                    .containsExactlyInAnyOrder("USER_CREATED", "USER_UPDATED");

            assertThat(loggedMessages())
                    .describedAs("the refused audit is named in the batch's rejection report")
                    .anyMatch(message -> message.contains(refused.getId()) && message.contains("Elasticsearch refused them"));
        }
    }

    @Test
    void anOutageDropsTheOldestBatchesInsteadOfGrowingWithoutBound() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        try (NetworkGate gate = NetworkGate.inFrontOf(elasticsearchHost, elasticsearchPort)) {
            ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index, gate.endpoint());
            configuration.setBulkActions(5);
            configuration.setMaxPendingBatches(2);
            configuration.setMaxConcurrentRequests(1);
            configuration.setRetryAttempts(1);
            configuration.setRetryInitialInterval(1L);
            configuration.setRetryMaxInterval(1L);

            try (ReporterHarness harness = ReporterHarness.start(configuration)) {
                gate.disconnect();

                for (int i = 0; i < 200; i++) {
                    harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, DAY));
                }

                await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                        .until(() -> loggedMessages().stream().anyMatch(message -> message.contains("backlog is full")));

                // the reporter is still alive and answering rather than wedged on a retry loop
                assertThat(harness.reporter().canSearch()).isTrue();
            }
        }
    }

    @Test
    void auditsReportedDuringARecoveredOutageStillArrive() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        try (NetworkGate gate = NetworkGate.inFrontOf(elasticsearchHost, elasticsearchPort)) {
            ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index, gate.endpoint());
            configuration.setRetryAttempts(10);
            configuration.setRetryInitialInterval(1L);
            configuration.setRetryMaxInterval(2L);

            try (ReporterHarness harness = ReporterHarness.start(configuration)) {
                gate.disconnect();
                harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, DAY));
                harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, DAY));

                gate.reconnect();

                await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                        .until(() -> searchDirect(index, domain) == 2);
            }
        }
    }

    @Test
    void stoppingImmediatelyAfterReportingStillWritesTheAudits() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index);
        // long enough that only the shutdown flush can get these out
        configuration.setFlushInterval(300L);
        configuration.setBulkActions(1000);

        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, DAY));
            harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, DAY));

            harness.stopReporter();

            await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
                    .until(() -> searchDirect(index, domain) == 2);
        }
    }

    @Test
    void shutdownAgainstAnUnreachableClusterCompletesWithinTheTimeoutAndRecordsTheLoss() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        try (NetworkGate gate = NetworkGate.inFrontOf(elasticsearchHost, elasticsearchPort)) {
            ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index, gate.endpoint());
            configuration.setFlushInterval(300L);
            configuration.setRetryAttempts(10);
            configuration.setRetryInitialInterval(5L);
            configuration.setRetryMaxInterval(30L);
            configuration.setShutdownFlushTimeout(2L);

            ReporterHarness harness = ReporterHarness.start(configuration);
            gate.disconnect();
            harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, DAY));

            long startedAt = System.nanoTime();
            harness.stopReporter();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(elapsed)
                    .describedAs("a sick cluster must not be able to hang a rolling restart")
                    .isLessThan(Duration.ofSeconds(20));
            // A reporter stopping against a cluster it never reached stops retrying its index
            // preparation rather than waiting out the flush timeout, so the loss is recorded as
            // nothing being writable. Batches for a reporter that did become writable are unaffected:
            // they still get the full flush window to be acknowledged.
            assertThat(loggedMessages())
                    .describedAs("audits that could not be written must be recorded, not lost silently")
                    .anyMatch(message -> message.contains("Dropped 1 audits"));

            harness.close();
        }
    }

    @Test
    void auditsReportedAfterStoppingAreRefusedRatherThanSilentlyLost() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();

        ReporterHarness harness = ReporterHarness.start(ReporterHarness.configurationFor(index));
        harness.stopReporter();

        Audit refused = AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, DAY);
        harness.reporter().report(refused);

        assertThat(loggedMessages())
                .anyMatch(message -> message.contains(refused.getId()) && message.contains("no longer accepts audits"));

        harness.close();
    }

    private String index() {
        String index = "am-audit-resilience-" + UUID.randomUUID();
        indexPatterns.add(index + "*");
        return index;
    }

    private List<String> loggedMessages() {
        return logs.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static Page<Audit> search(ReporterHarness harness, String domain) {
        return harness.reporter()
                .search(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), 0, 20)
                .blockingGet();
    }

    /** Reads straight from Elasticsearch, so a stopped reporter can still be asserted against. */
    private static long searchDirect(String index, String domain) {
        elasticsearch.refresh(index + "-*");
        return elasticsearch.getJson("/" + index + "-*/_count?q=referenceId:%22" + domain + "%22")
                .path("count").asLong();
    }
}
