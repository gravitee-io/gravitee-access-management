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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The proxy settings are only worth having if they actually route the traffic, so rather than
 * inspecting the client configuration these point a reporter at the live container through a proxy
 * that is not there. Reaching the cluster anyway would mean the proxy had been ignored — which is
 * exactly what happened before these settings existed.
 *
 * @author GraviteeSource Team
 */
class ProxyConfigurationTest {

    /** Nothing listens here, so any connection through it fails. */
    private static final int DEAD_PROXY_PORT = 1;

    @Test
    void withoutAProxyTheReporterReachesTheCluster() throws Exception {
        try (ReporterHarness harness = ReporterHarness.start(ReporterHarness.configurationFor("proxy-control"))) {
            await().atMost(30, SECONDS).untilAsserted(() ->
                    assertThat(harness.reporter().status()).isEqualTo(ReporterStatus.READY));
        }
    }

    @Test
    void aProxyThatIsNotThereKeepsTheReporterFromReachingTheCluster() throws Exception {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("proxy-dead");
        configuration.setProxyHost("localhost");
        configuration.setProxyPort(DEAD_PROXY_PORT);
        configuration.setRetryMaxInterval(1L);

        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            // an unreachable cluster is treated as transient, so it retries and stays STARTING rather
            // than failing outright — either way it must not be READY
            Thread.sleep(5_000);

            assertThat(harness.reporter().status())
                    .describedAs("the container is up, so becoming READY would mean the proxy was bypassed")
                    .isEqualTo(ReporterStatus.STARTING);
        }
    }

    @Test
    void aProxyHostWithoutAPortIsRejectedWithAUsefulMessage() {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("proxy-no-port");
        configuration.setProxyHost("proxy.internal");

        assertThatThrownBy(() -> ReporterHarness.start(configuration))
                .rootCause()
                .hasMessageContaining("proxy at 'proxy.internal'")
                .hasMessageContaining("port is null");
    }

    @Test
    void anOutOfRangeProxyPortIsRejectedWithAUsefulMessage() {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("proxy-bad-port");
        configuration.setProxyHost("proxy.internal");
        configuration.setProxyPort(70000);

        assertThatThrownBy(() -> ReporterHarness.start(configuration))
                .rootCause()
                .hasMessageContaining("port is 70000");
    }

    @Test
    void anUnsupportedProxyTypeIsRejectedWithAUsefulMessage() {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("proxy-bad-type");
        configuration.setProxyHost("proxy.internal");
        configuration.setProxyPort(3128);
        configuration.setProxyType("carrier-pigeon");

        assertThatThrownBy(() -> ReporterHarness.start(configuration))
                .rootCause()
                .hasMessageContaining("Unsupported Elasticsearch proxy type 'carrier-pigeon'")
                .hasMessageContaining("SOCKS5");
    }

    @Test
    void theProxyTypeIsNotCaseSensitive() throws Exception {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("proxy-lowercase-type");
        configuration.setProxyHost("localhost");
        configuration.setProxyPort(DEAD_PROXY_PORT);
        configuration.setProxyType("socks5");

        // starts cleanly: the type is accepted, even though the proxy itself is not reachable
        try (ReporterHarness harness = ReporterHarness.start(configuration)) {
            assertThat(harness.reporter().status()).isNotEqualTo(ReporterStatus.FAILED);
        }
    }
}
