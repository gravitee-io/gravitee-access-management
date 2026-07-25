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

import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @BeforeAll
    static void connect() {
        elasticsearch = ElasticsearchTestClient.onTestContainer();
    }

    @AfterEach
    void cleanUp() {
        createdIndexPatterns.forEach(pattern -> elasticsearch.cleanUp(pattern));
        createdIndexPatterns.clear();
    }

    @Test
    void startupFailsLoudlyWhenAnotherTemplateAlreadyOwnsThePattern() {
        String index = index();
        // an existing composable template matching the same pattern at the same priority: Elasticsearch
        // refuses ours outright, which is exactly the collision two overlapping AM reporters produce
        HttpResponse<String> existing = elasticsearch.put("/_index_template/" + index + "-conflicting", """
                {"index_patterns": ["%s-*"], "priority": %d, "template": {"mappings": {}}}"""
                .formatted(index, AuditIndexTemplate.priority(index)));
        assertThat(existing.statusCode()).isEqualTo(200);

        assertThatThrownBy(() -> ReporterHarness.start(ReporterHarness.configurationFor(index)))
                .hasStackTraceContaining("Unable to apply the Elasticsearch index template")
                .hasStackTraceContaining(AuditIndexTemplate.name(index))
                .hasStackTraceContaining(index + "-*")
                .hasStackTraceContaining("will not run without its template");
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
