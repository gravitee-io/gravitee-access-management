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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Round trip through the reporter's own SPI: an audit reported here must come back out of search
 * with its identifying fields intact.
 *
 * @author GraviteeSource Team
 */
class ElasticsearchAuditReporterTest {

    private static final String INDEX = "am-audit-roundtrip-" + UUID.randomUUID();

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
    void reportedAuditIsSearchableWithItsFieldsIntact() {
        String domain = "domain-" + UUID.randomUUID();
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Audit reported = AuditFixtures.audit(domain, "USER_CREATED", Status.SUCCESS, timestamp);

        harness.reporter().report(reported);

        Audit found = awaitSingleAudit(domain);

        assertThat(found.getId()).isEqualTo(reported.getId());
        assertThat(found.getTransactionId()).isEqualTo(reported.getTransactionId());
        assertThat(found.getType()).isEqualTo("USER_CREATED");
        assertThat(found.getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(found.getReferenceId()).isEqualTo(domain);
        assertThat(found.timestamp()).isEqualTo(timestamp);

        assertThat(found.getActor().getId()).isEqualTo("actor-id");
        assertThat(found.getActor().getAlternativeId()).isEqualTo("actor-alternative-id");
        assertThat(found.getActor().getDisplayName()).isEqualTo("actor display name");
        assertThat(found.getActor().getAttributes()).containsEntry("tenant", "acme");

        assertThat(found.getTarget().getId()).isEqualTo("target-id");
        assertThat(found.getTarget().getDisplayName()).isEqualTo("target display name");

        assertThat(found.getAccessPoint().getId()).isEqualTo("access-point-id");
        assertThat(found.getAccessPoint().getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(found.getAccessPoint().getUserAgent()).isEqualTo("Chrome");

        assertThat(found.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void reportingTheSameAuditTwiceYieldsOneRecord() {
        String domain = "domain-" + UUID.randomUUID();
        Audit audit = AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.now());

        harness.reporter().report(audit);
        harness.reporter().report(audit);

        awaitSingleAudit(domain);
        assertThat(search(domain).getTotalCount()).isEqualTo(1);
    }

    @Test
    void reporterAnswersSearchRequests() {
        assertThat(harness.reporter().canSearch()).isTrue();
    }

    private Audit awaitSingleAudit(String domain) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(() -> search(domain).getData().size() == 1);
        elasticsearch.awaitSearchable(INDEX + "-*");
        return search(domain).getData().iterator().next();
    }

    private Page<Audit> search(String domain) {
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().build();
        return harness.reporter().search(ReferenceType.DOMAIN, domain, criteria, 0, 10).blockingGet();
    }
}
