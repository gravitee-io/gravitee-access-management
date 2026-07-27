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
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A batch is serialized when the buffer fills, and the thread that fills it is whichever one reported
 * the last audit — in the gateway a Vert.x event loop that is also serving authentication requests.
 * Serializing up to {@code bulkActions} documents there would put that CPU work on the loop, so it has
 * to happen somewhere else.
 * <p>
 * No cluster is needed: serialization happens before anything is sent, which is the point.
 *
 * @author GraviteeSource Team
 */
class SerializationThreadTest {

    /** Records the thread that reads it, which only happens while the audit is being serialized. */
    private static class ThreadCapturingAudit extends Audit {
        private final AtomicReference<String> serializedOn;

        ThreadCapturingAudit(Audit source, AtomicReference<String> serializedOn) {
            setId(source.getId());
            setTransactionId(source.getTransactionId());
            setReferenceType(source.getReferenceType());
            setReferenceId(source.getReferenceId());
            setTimestamp(source.timestamp());
            setOutcome(source.getOutcome());
            setActor(source.getActor());
            setTarget(source.getTarget());
            setAccessPoint(source.getAccessPoint());
            this.serializedOn = serializedOn;
            setType(source.getType());
        }

        @Override
        public String getType() {
            if (serializedOn != null) {
                serializedOn.compareAndSet(null, Thread.currentThread().getName());
            }
            return super.getType();
        }
    }

    @Test
    void batchesAreSerializedOffTheThreadThatReportedTheAudit() throws Exception {
        // no cluster, so nothing is ever sent — but the batch is still built
        ElasticsearchReporterConfiguration configuration =
                ReporterHarness.configurationFor("serialization-thread", "http://localhost:1");
        configuration.setBulkActions(4);
        // long enough that the batch is emitted by hitting the count, which is the path that would
        // otherwise serialize inline on the reporting thread
        configuration.setFlushInterval(300L);

        AtomicReference<String> serializedOn = new AtomicReference<>();
        String reportingThread = "audit-reporting-thread";

        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            Thread reporter = new Thread(() -> {
                for (int i = 0; i < 4; i++) {
                    Audit audit = AuditFixtures.audit("a-domain", "USER_LOGIN", Status.SUCCESS, Instant.now());
                    harness.reporter().report(new ThreadCapturingAudit(audit, serializedOn));
                }
            }, reportingThread);
            reporter.start();
            reporter.join();

            await().atMost(30, SECONDS).untilAsserted(() -> assertThat(serializedOn.get()).isNotNull());

            assertThat(serializedOn.get())
                    .describedAs("serialization must not run on the thread that reported the audit")
                    .isNotEqualTo(reportingThread);
        }
    }
}
