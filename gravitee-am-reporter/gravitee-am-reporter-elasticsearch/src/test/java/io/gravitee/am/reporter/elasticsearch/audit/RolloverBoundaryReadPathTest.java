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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The rollover period decides the write index while the read wildcard ignores it, so the pairing has
 * to be checked against a real server: a period whose boundary is misplaced, or a wildcard that stops
 * matching, hides audits rather than failing.
 *
 * @author GraviteeSource Team
 */
class RolloverBoundaryReadPathTest {

    private static ElasticsearchTestClient elasticsearch;

    @BeforeAll
    static void connect() {
        elasticsearch = ElasticsearchTestClient.onTestContainer();
    }

    private static Stream<Arguments> boundaries() {
        return Stream.of(
                // last instant of one period, first instant of the next
                Arguments.of(IndexRolloverPeriod.DAILY,
                        Instant.parse("2026-03-01T23:59:59Z"), Instant.parse("2026-03-02T00:00:00Z")),
                Arguments.of(IndexRolloverPeriod.WEEKLY,
                        Instant.parse("2026-07-26T23:59:59Z"), Instant.parse("2026-07-27T00:00:00Z")),
                Arguments.of(IndexRolloverPeriod.MONTHLY,
                        Instant.parse("2026-07-31T23:59:59Z"), Instant.parse("2026-08-01T00:00:00Z")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaries")
    void writesEitherSideOfARolloverBoundaryIntoSeparateIndicesAndReadsBothBack(
            IndexRolloverPeriod period, Instant closing, Instant opening) throws Exception {
        String index = indexName(period.name());
        try (ReporterHarness harness = ReporterHarness.start(configurationFor(index, period))) {
            String domain = domain();
            harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, closing));
            harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, opening));

            Page<Audit> page = awaitAudits(harness, index, domain, 2);

            assertThat(page.getData()).extracting(Audit::getType)
                    .describedAs("both sides of the boundary must come back through one search")
                    .containsExactlyInAnyOrder("USER_CREATED", "USER_LOGIN");
            assertThat(indexNames(index)).contains(
                    AuditIndexNames.writeIndex(index, period, closing),
                    AuditIndexNames.writeIndex(index, period, opening));
        } finally {
            elasticsearch.cleanUp(index + "*");
        }
    }

    /**
     * The upgrade path. An operator who lengthens the period must not lose sight of what was written
     * under the old one, which works only because the read wildcard never mentions the period.
     */
    @Test
    void readsIndicesWrittenBeforeThePeriodWasChanged() throws Exception {
        String index = indexName("changed");
        String domain = domain();
        Instant underDaily = Instant.parse("2026-09-10T10:00:00Z");
        Instant underMonthly = Instant.parse("2026-09-20T10:00:00Z");

        try {
            try (ReporterHarness daily = ReporterHarness.start(configurationFor(index, IndexRolloverPeriod.DAILY))) {
                daily.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, underDaily));
                awaitAudits(daily, index, domain, 1);
            }

            try (ReporterHarness monthly = ReporterHarness.start(configurationFor(index, IndexRolloverPeriod.MONTHLY))) {
                monthly.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, underMonthly));

                Page<Audit> page = awaitAudits(monthly, index, domain, 2);

                assertThat(page.getData()).extracting(Audit::getType)
                        .describedAs("the audit written under daily rollover is still readable after the change")
                        .containsExactlyInAnyOrder("USER_CREATED", "USER_LOGIN");
                assertThat(indexNames(index)).contains(index + "-2026.09.10", index + "-2026.09");
            }
        } finally {
            elasticsearch.cleanUp(index + "*");
        }
    }

    /**
     * A period the reporter cannot interpret must behave like an illegal index name: the reporter
     * starts, refuses to write, and says why. Throwing out of bean creation instead would abort
     * loading every other reporter on the domain, and falling back to daily would quietly write
     * audits somewhere the operator did not choose.
     */
    @Test
    void anUnusablePeriodDropsAuditsLoudlyRatherThanWritingThemAnyway() throws Exception {
        String index = indexName("unusable");
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index);
        configuration.setRolloverPeriod("hourly");

        ListAppender<ILoggingEvent> logs = captureLogs();
        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            harness.reporter().report(AuditFixtures.audit(domain(), "USER_CREATED", Status.SUCCESS, Instant.parse("2026-11-01T10:00:00Z")));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(250, TimeUnit.MILLISECONDS)
                    .until(() -> loggedText(logs).stream().anyMatch(text -> text.contains("not writable")));

            assertThat(loggedText(logs))
                    .describedAs("the operator needs the rejected value and what is accepted instead")
                    .anyMatch(text -> text.contains("hourly") && text.contains("daily, weekly, monthly"));
            assertThat(elasticsearch.indices(index + "-*"))
                    .describedAs("nothing may be written under a period nobody configured")
                    .isEmpty();
        } finally {
            releaseLogs(logs);
            elasticsearch.cleanUp(index + "*");
        }
    }

    /** One template covers every period, because its pattern is the period-independent read wildcard. */
    @Test
    void appliesOneTemplateWhateverThePeriod() throws Exception {
        String index = indexName("template");
        try (ReporterHarness harness = ReporterHarness.start(configurationFor(index, IndexRolloverPeriod.MONTHLY))) {
            elasticsearch.awaitTemplate(AuditIndexTemplate.name(index));
            harness.reporter().report(AuditFixtures.audit(domain(), "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-10-01T10:00:00Z")));

            JsonNode template = elasticsearch.getJson("/_index_template/" + AuditIndexTemplate.name(index));
            JsonNode entry = template.get("index_templates").get(0).get("index_template");

            assertThat(entry.get("index_patterns").get(0).asText()).isEqualTo(index + "-*");
            assertThat(entry.get("priority").asInt()).isEqualTo(AuditIndexTemplate.priority(index));
        } finally {
            elasticsearch.cleanUp(index + "*");
        }
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logs.start();
        rootLogger().addAppender(logs);
        return logs;
    }

    private static void releaseLogs(ListAppender<ILoggingEvent> logs) {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    /** Message plus causes, since the rejected value lives on the wrapped exception. */
    private static List<String> loggedText(ListAppender<ILoggingEvent> logs) {
        return logs.list.stream().map(event -> {
            StringBuilder text = new StringBuilder(event.getFormattedMessage());
            for (var cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
                text.append('\n').append(cause.getClassName()).append(": ").append(cause.getMessage());
            }
            return text.toString();
        }).toList();
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static ElasticsearchReporterConfiguration configurationFor(String index, IndexRolloverPeriod period) {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index);
        configuration.setRolloverPeriod(period.name().toLowerCase(Locale.ROOT));
        return configuration;
    }

    private static String indexName(String discriminator) {
        return "am-audit-rollover-" + discriminator.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
    }

    private static String domain() {
        return "domain-" + UUID.randomUUID();
    }

    private static List<String> indexNames(String index) {
        elasticsearch.refresh(index + "-*");
        List<String> names = new ArrayList<>();
        elasticsearch.indices(index + "-*").forEach(node -> names.add(node.get("index").asText()));
        return names;
    }

    private static Page<Audit> awaitAudits(ReporterHarness harness, String index, String domain, int expected) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(() -> search(harness, domain).getData().size() == expected);
        elasticsearch.awaitSearchable(index + "-*");
        return search(harness, domain);
    }

    private static Page<Audit> search(ReporterHarness harness, String domain) {
        return harness.reporter()
                .search(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), 0, 20)
                .blockingGet();
    }
}
