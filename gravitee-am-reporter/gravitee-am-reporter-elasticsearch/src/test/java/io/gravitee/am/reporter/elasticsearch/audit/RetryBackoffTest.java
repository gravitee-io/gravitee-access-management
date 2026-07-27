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

import io.gravitee.am.reporter.elasticsearch.ElasticsearchReporterConfiguration;
import io.gravitee.am.reporter.elasticsearch.ReporterHarness;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batches retry concurrently and a backlog retries behind them. Without jitter they all wake at the
 * same instant, so a cluster coming back up is hit by the entire backlog at once and knocked over
 * again.
 *
 * @author GraviteeSource Team
 */
class RetryBackoffTest {

    private ElasticsearchAuditReporter reporterWith(long initial, long max) {
        ElasticsearchReporterConfiguration configuration = ReporterHarness.configurationFor("backoff", "http://localhost:1");
        configuration.setRetryInitialInterval(initial);
        configuration.setRetryMaxInterval(max);

        ElasticsearchAuditReporter reporter = new ElasticsearchAuditReporter();
        ReflectionTestUtils.setField(reporter, "configuration", configuration);
        return reporter;
    }

    @Test
    void backsOffExponentiallyUpToTheConfiguredCeiling() {
        ElasticsearchAuditReporter reporter = reporterWith(4L, 60L);

        // the jitter only shortens, so each attempt sits within its exponential step
        assertThat(reporter.backoffSeconds(0)).isBetween(3L, 4L);
        assertThat(reporter.backoffSeconds(1)).isBetween(6L, 8L);
        assertThat(reporter.backoffSeconds(2)).isBetween(12L, 16L);

        assertThat(IntStream.range(0, 200).mapToLong(i -> reporter.backoffSeconds(20)).max().orElseThrow())
                .describedAs("never past the configured maximum, however many attempts have gone by")
                .isLessThanOrEqualTo(60L);
    }

    @Test
    void spreadsRetriesSoABacklogDoesNotWakeInLockstep() {
        ElasticsearchAuditReporter reporter = reporterWith(30L, 30L);

        Set<Long> delays = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            delays.add(reporter.backoffSeconds(5));
        }

        assertThat(delays)
                .describedAs("200 batches retrying at the ceiling must not all pick the same instant")
                .hasSizeGreaterThan(1);
        assertThat(delays).allSatisfy(delay -> assertThat(delay).isBetween(23L, 30L));
    }

    @Test
    void doesNotJitterADelayThatIsAlreadyTheShortestPossible() {
        ElasticsearchAuditReporter reporter = reporterWith(1L, 1L);

        // a one second ceiling has nothing to spread, and must not become zero or negative
        assertThat(IntStream.range(0, 50).mapToLong(i -> reporter.backoffSeconds(i)).min().orElseThrow())
                .isEqualTo(1L);
    }
}
