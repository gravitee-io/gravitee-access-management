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

import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An install with a reporter per domain, all pointing at the same cluster, must hold one Elasticsearch
 * client rather than one per domain — the same sharing the TCP and Kafka reporters do for their write
 * streams. These run against the real container, so they also cover the wiring: the shared client is
 * autowired and initialized outside Spring's bean registry, and each reporter sees it through its own
 * wrapper.
 *
 * @author GraviteeSource Team
 */
class SharedClientLifecycleTest {

    @Test
    void twoReportersOnTheSameClusterHoldOneClient() throws Exception {
        WriteStreamRegistry registry = new WriteStreamRegistry();

        ReporterHarness first = ReporterHarness.start(ReporterHarness.configurationFor("shared-client-one"), registry);
        ReporterHarness second = ReporterHarness.start(ReporterHarness.configurationFor("shared-client-two"), registry);
        try {
            assertThat(registry.size())
                    .describedAs("two reporters writing different indices to one cluster share its client")
                    .isEqualTo(1);
        } finally {
            second.close();
            first.close();
        }

        assertThat(registry.size())
                .describedAs("the client is released once the last reporter using it has stopped")
                .isZero();
    }

    @Test
    void aReporterOnAnotherClusterGetsItsOwnClient() throws Exception {
        WriteStreamRegistry registry = new WriteStreamRegistry();

        ElasticsearchReporterConfiguration elsewhere = ReporterHarness.configurationFor("shared-client-elsewhere");
        elsewhere.setEndpoints(List.of("http://localhost:1"));

        ReporterHarness onContainer = ReporterHarness.start(ReporterHarness.configurationFor("shared-client-here"), registry);
        // never reachable, but the client is built and shared before anything is written through it
        ReporterHarness onNothing = ReporterHarness.start(elsewhere, registry);
        try {
            assertThat(registry.size()).isEqualTo(2);
        } finally {
            onNothing.close();
            onContainer.close();
        }
    }

    @Test
    void stoppingOneReporterLeavesTheClientForTheOther() throws Exception {
        WriteStreamRegistry registry = new WriteStreamRegistry();

        ReporterHarness kept = ReporterHarness.start(ReporterHarness.configurationFor("shared-client-kept"), registry);
        ReporterHarness stopped = ReporterHarness.start(ReporterHarness.configurationFor("shared-client-stopped"), registry);
        try {
            stopped.close();

            assertThat(registry.size())
                    .describedAs("the client outlives a reporter that stops while another is still using it")
                    .isEqualTo(1);
        } finally {
            kept.close();
        }
    }
}
