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

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Daily indices and the read path over them.
 *
 * @author GraviteeSource Team
 */
class AuditIndexITests {

    private static final String INDEX = "am-audit-daily-" + UUID.randomUUID();

    private static ReporterHarness harness;
    private static ElasticsearchTestClient elasticsearch;

    @BeforeAll
    static void startReporter() throws Exception {
        elasticsearch = ElasticsearchTestClient.onTestContainer();
        harness = ReporterHarness.start(ReporterHarness.configurationFor(INDEX));
    }

    @AfterAll
    static void stopReporter() {
        if (harness != null) {
            harness.close();
        }
        elasticsearch.cleanUp(INDEX + "*");
    }

    @Test
    void writesEachAuditToTheDailyIndexItsOwnTimestampFallsIn() {
        String domain = domain();
        Audit older = AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.parse("2026-03-01T10:00:00Z"));
        Audit newer = AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-03-02T10:00:00Z"));

        harness.reporter().report(older);
        harness.reporter().report(newer);
        awaitAudits(domain, 2);

        List<String> indices = indexNames();
        assertThat(indices).contains(INDEX + "-2026.03.01", INDEX + "-2026.03.02");
    }

    @Test
    void readsBackAcrossEveryDailyIndex() {
        String domain = domain();
        harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.parse("2026-04-01T10:00:00Z")));
        harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-04-09T10:00:00Z")));

        Page<Audit> page = awaitAudits(domain, 2);

        assertThat(page.getData()).extracting(Audit::getType)
                .containsExactlyInAnyOrder("USER_CREATED", "USER_LOGIN");
    }

    @Test
    void anAuditReportedTwiceAcrossADateBoundaryStaysOneDocument() {
        String domain = domain();
        Audit audit = AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.parse("2026-05-05T23:59:59Z"));

        harness.reporter().report(audit);
        awaitAudits(domain, 1);
        // a retry that happens after midnight still derives its index from the audit's own timestamp
        harness.reporter().report(audit);
        awaitAudits(domain, 1);

        assertThat(search(domain).getTotalCount()).isEqualTo(1);
        assertThat(indexNames()).doesNotContain(INDEX + "-2026.05.06");
    }

    @Test
    void reportsATotalThatIsExactBeyondTheDefaultTrackingLimit() {
        String domain = domain();
        List<String> documents = new ArrayList<>();
        for (int i = 0; i < 10_001; i++) {
            documents.add("""
                    {"id":"bulk-%d","referenceType":"DOMAIN","referenceId":"%s","type":"USER_LOGIN","timestamp":%d,"outcome":{"status":"SUCCESS"}}"""
                    .formatted(i, domain, Instant.parse("2026-06-01T10:00:00Z").toEpochMilli()));
        }
        elasticsearch.bulkIndex(INDEX + "-2026.06.01", documents);
        elasticsearch.refresh(INDEX + "-*");

        assertThat(search(domain).getTotalCount())
                .describedAs("the default tracking limit would silently cap this at 10000")
                .isEqualTo(10_001);
    }

    @Test
    void refusesToPageBeyondTheResultWindowRatherThanReturningWrongData() {
        assertThatThrownBy(() -> harness.reporter()
                .search(ReferenceType.DOMAIN, domain(), new AuditReportableCriteria.Builder().build(), 200, 100)
                .blockingGet())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot page past the first 10000 audits");
    }

    @Test
    void groupsBySupportedFieldsIntoPopulatedBuckets() {
        String domain = domain();
        harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-06-10T10:00:00Z")));
        harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.FAILURE, Instant.parse("2026-06-10T11:00:00Z")));
        harness.reporter().report(AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.parse("2026-06-10T12:00:00Z")));
        awaitAudits(domain, 3);

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().field("type").build();
        Map<Object, Object> buckets = harness.reporter()
                .aggregate(ReferenceType.DOMAIN, domain, criteria, Type.GROUP_BY)
                .blockingGet();

        assertThat(buckets).containsEntry("USER_LOGIN", 2L).containsEntry("USER_CREATED", 1L);
    }

    @Test
    void countsAuditsForAReference() {
        String domain = domain();
        harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-06-11T10:00:00Z")));
        harness.reporter().report(AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.parse("2026-06-11T11:00:00Z")));
        awaitAudits(domain, 2);

        Map<Object, Object> count = harness.reporter()
                .aggregate(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), Type.COUNT)
                .blockingGet();

        assertThat(count).containsEntry("data", 2L);
    }

    @Test
    void resolvesASingleAuditById() {
        String domain = domain();
        Audit audit = AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, Instant.parse("2026-06-12T10:00:00Z"));
        harness.reporter().report(audit);
        awaitAudits(domain, 1);

        Audit found = harness.reporter().findById(ReferenceType.DOMAIN, domain, audit.getId()).blockingGet();

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(audit.getId());
        assertThat(found.getType()).isEqualTo("USER_CREATED");
    }

    @Test
    void appliesTheIndexTemplateOnACleanCluster() {
        JsonNode template = elasticsearch.getJson("/_index_template/" + AuditIndexTemplate.name(INDEX));
        JsonNode entry = template.get("index_templates").get(0).get("index_template");

        assertThat(entry.get("index_patterns").get(0).asText()).isEqualTo(INDEX + "-*");
        assertThat(entry.get("priority").asInt()).isEqualTo(AuditIndexTemplate.priority(INDEX));
    }

    private static String domain() {
        return "domain-" + UUID.randomUUID();
    }

    private static List<String> indexNames() {
        elasticsearch.refresh(INDEX + "-*");
        List<String> names = new ArrayList<>();
        elasticsearch.indices(INDEX + "-*").forEach(node -> names.add(node.get("index").asText()));
        return names;
    }

    private static Page<Audit> awaitAudits(String domain, int expected) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(() -> search(domain).getData().size() == expected);
        return search(domain);
    }

    private static Page<Audit> search(String domain) {
        return harness.reporter()
                .search(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), 0, 20)
                .blockingGet();
    }
}
