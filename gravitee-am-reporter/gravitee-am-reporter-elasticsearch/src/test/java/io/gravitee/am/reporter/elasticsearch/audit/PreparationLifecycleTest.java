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

import io.gravitee.am.model.ReporterStatus;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * An unreachable cluster is retried indefinitely, and a reporter is stopped and restarted on every
 * reporter update event. If stopping does not end the retry, each restart leaves another loop running
 * for the lifetime of the node.
 * <p>
 * The retry interval is set to zero so the reporter's own attempts are effectively continuous. The
 * shared Elasticsearch client keeps its own periodic probe running against the same endpoint — that
 * timer belongs to the client library and is a documented limitation — so this asserts on the
 * difference in magnitude rather than on silence: a live retry produces attempts without pause, a
 * stopped one leaves only the library's occasional probe.
 *
 * @author GraviteeSource Team
 */
class PreparationLifecycleTest {

    private static final int LIBRARY_PROBE_ALLOWANCE = 6;

    @Test
    void stopsRetryingIndexPreparationOnceTheReporterHasStopped() throws Exception {
        AtomicInteger connections = new AtomicInteger();

        // accepts and immediately closes, so every attempt is counted and every request fails
        try (ServerSocket blackHole = new ServerSocket(0)) {
            Thread accepter = new Thread(() -> {
                while (!blackHole.isClosed()) {
                    try (Socket socket = blackHole.accept()) {
                        connections.incrementAndGet();
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
            accepter.setDaemon(true);
            accepter.start();

            ElasticsearchReporterConfiguration configuration =
                    ReporterHarness.configurationFor("preparation-lifecycle", "http://localhost:" + blackHole.getLocalPort());
            configuration.setRetryMaxInterval(0L);

            ReporterHarness harness = ReporterHarness.start(configuration);
            try {
                // it has to actually be retrying, or stopping it proves nothing
                await().atMost(30, SECONDS).untilAsserted(() -> assertThat(connections.get()).isGreaterThanOrEqualTo(20));

                assertThat(harness.reporter().status())
                        .describedAs("an unreachable cluster is transient, and audit reads must not be diverted "
                                + "elsewhere on the strength of a slow start")
                        .isEqualTo(ReporterStatus.STARTING);

                harness.stopReporter();
                int whenStopped = connections.get();

                Thread.sleep(3_000);

                assertThat(connections.get() - whenStopped)
                        .describedAs("connection attempts in the 3s after the reporter was stopped; an uncancelled "
                                + "retry at a zero interval produces far more than the client library's own probe")
                        .isLessThanOrEqualTo(LIBRARY_PROBE_ALLOWANCE);
            } finally {
                harness.close();
            }
        }
    }
}
