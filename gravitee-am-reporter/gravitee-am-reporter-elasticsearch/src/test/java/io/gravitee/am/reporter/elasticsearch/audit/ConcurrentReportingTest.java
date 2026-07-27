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
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchTestClient;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Audits arrive from the event bus, and an inherited organization reporter registers a consumer per
 * domain, so {@code report} is genuinely called from several threads at once. A processor that is not
 * serialized has undefined behaviour under that, which shows up as audits that quietly never arrive.
 *
 * @author GraviteeSource Team
 */
class ConcurrentReportingTest {

    private static final int THREADS = 8;
    private static final int AUDITS_PER_THREAD = 250;

    private final String index = "concurrent-reporting-" + UUID.randomUUID();
    private final ElasticsearchTestClient elasticsearch = ElasticsearchTestClient.onTestContainer();
    private ExecutorService reporters;

    @AfterEach
    void tearDown() {
        if (reporters != null) {
            reporters.shutdownNow();
        }
        elasticsearch.cleanUp(index + "-*");
    }

    @Test
    void everyAuditReportedConcurrentlyIsIndexed() throws Exception {
        String domain = "domain-" + UUID.randomUUID();
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor(index);
        configuration.setBulkActions(100);
        configuration.setFlushInterval(1L);
        // large enough that nothing is dropped for backlog reasons, so a missing audit means a lost signal
        configuration.setMaxPendingBatches(500);

        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            await().atMost(60, SECONDS).untilAsserted(() ->
                    assertThat(harness.reporter().status()).isEqualTo(io.gravitee.am.model.ReporterStatus.READY));

            reporters = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);

            for (int t = 0; t < THREADS; t++) {
                reporters.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < AUDITS_PER_THREAD; i++) {
                            harness.reporter().report(
                                    AuditFixtures.audit(domain, "USER_LOGIN", Status.SUCCESS, Instant.now()));
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(60, SECONDS)).isTrue();

            int expected = THREADS * AUDITS_PER_THREAD;
            await().atMost(90, SECONDS).untilAsserted(() -> {
                elasticsearch.refresh(index + "-*");
                long indexed = harness.reporter()
                        .search(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), 0, 1)
                        .blockingGet()
                        .getTotalCount();
                assertThat(indexed)
                        .describedAs("every audit reported from %d threads must reach Elasticsearch", THREADS)
                        .isEqualTo(expected);
            });
        }
    }
}
