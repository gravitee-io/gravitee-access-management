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
import io.gravitee.am.model.ReporterStatus;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The index template is what stops free-form audit attributes being dynamically mapped, so a
 * reporter that cannot apply it must not run. This covers both halves: the failure is loud, and
 * without the template documents really are rejected.
 *
 * @author GraviteeSource Team
 */
class IndexTemplateFailureTest {

    private static ElasticsearchTestClient elasticsearch;

    private final List<String> createdIndexPatterns = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeAll
    static void connect() {
        elasticsearch = ElasticsearchTestClient.onTestContainer();
    }

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logs.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    private List<String> loggedMessages() {
        return logs.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Full text including causes, since the actionable detail lives on the wrapped exception. */
    private List<String> loggedThrowables() {
        return logs.list.stream()
                .map(ILoggingEvent::getThrowableProxy)
                .filter(java.util.Objects::nonNull)
                .map(proxy -> {
                    StringWriter text = new StringWriter();
                    PrintWriter writer = new PrintWriter(text);
                    for (var current = proxy; current != null; current = current.getCause()) {
                        writer.println(current.getClassName() + ": " + current.getMessage());
                    }
                    return text.toString();
                })
                .toList();
    }

    @AfterEach
    void cleanUp() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        logs.stop();
        createdIndexPatterns.forEach(pattern -> elasticsearch.cleanUp(pattern));
        createdIndexPatterns.clear();
    }

    @Test
    void aTemplateCollisionIsReportedLoudlyAndNothingIsWritten() throws Exception {
        String index = index();
        String domain = "domain-" + UUID.randomUUID();
        // an existing composable template matching the same pattern at the same priority: Elasticsearch
        // refuses ours outright, which is exactly the collision two overlapping AM reporters produce
        HttpResponse<String> existing = elasticsearch.put("/_index_template/" + index + "-conflicting", """
                {"index_patterns": ["%s-*"], "priority": %d, "template": {"mappings": {}}}"""
                .formatted(index, AuditIndexTemplate.priority(index)));
        assertThat(existing.statusCode()).isEqualTo(200);

        try (ReporterHarness harness = ReporterHarness.start(ReporterHarness.configurationFor(index))) {
            harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.now()));

            await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
                    .until(() -> loggedMessages().stream()
                            .anyMatch(message -> message.contains("not writable")));

            assertThat(loggedThrowables())
                    .describedAs("the operator needs the template name, its pattern and what to change")
                    .anyMatch(text -> text.contains("Unable to apply the Elasticsearch index template")
                            && text.contains(AuditIndexTemplate.name(index))
                            && text.contains(index + "-*")
                            && text.contains("will not run without its template"));

            // nothing reached Elasticsearch: an index without the template is exactly what must not happen
            assertThat(elasticsearch.indices(index + "-*")).isEmpty();

            assertThat(harness.reporter().status())
                    .describedAs("a collision is a misconfiguration, not a slow start, so audit reads have to be "
                            + "able to fall through to a reporter that can answer them")
                    .isEqualTo(ReporterStatus.FAILED);
        }
    }

    @Test
    void withoutTheTemplateAnAttributeTypeChangeRejectsTheDocument() {
        // no template, so Elasticsearch maps actor.attributes dynamically: first seen type wins
        String index = index() + "-2026.07.25";
        createdIndexPatterns.add(index);

        HttpResponse<String> asString = elasticsearch.put("/" + index + "/_doc/first", """
                {"id":"first","actor":{"attributes":{"probe":"a string"}}}""");
        assertThat(asString.statusCode()).isIn(200, 201);

        HttpResponse<String> asObject = elasticsearch.put("/" + index + "/_doc/second", """
                {"id":"second","actor":{"attributes":{"probe":{"nested":"an object"}}}}""");

        assertThat(asObject.statusCode())
                .describedAs("this rejection is why the template is not optional")
                .isEqualTo(400);
        assertThat(asObject.body()).contains("document_parsing_exception");
    }

    @Test
    void withTheTemplateBothShapesOfTheSameAttributeAreAccepted() throws Exception {
        String index = index();
        createdIndexPatterns.add(index + "*");

        try (ReporterHarness harness = ReporterHarness.start(ReporterHarness.configurationFor(index))) {
            assertThat(harness.reporter().canSearch()).isTrue();
            elasticsearch.awaitTemplate(AuditIndexTemplate.name(index));
            await().atMost(30, TimeUnit.SECONDS).until(() -> harness.reporter().status() == ReporterStatus.READY);

            String dailyIndex = index + "-2026.07.25";
            HttpResponse<String> asString = elasticsearch.put("/" + dailyIndex + "/_doc/first", """
                    {"id":"first","actor":{"attributes":{"probe":"a string"}}}""");
            HttpResponse<String> asObject = elasticsearch.put("/" + dailyIndex + "/_doc/second", """
                    {"id":"second","actor":{"attributes":{"probe":{"nested":"an object"}}}}""");

            assertThat(asString.statusCode()).isIn(200, 201);
            assertThat(asObject.statusCode()).isIn(200, 201);
        }
    }

    private String index() {
        String index = "am-audit-template-" + UUID.randomUUID();
        createdIndexPatterns.add(index + "*");
        return index;
    }
}
