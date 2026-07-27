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
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An install runs one reporter per domain, so drop counters that are not tagged per reporter tell an
 * operator that audits are being lost but not by whom.
 *
 * @author GraviteeSource Team
 */
class AuditDropCounterTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void dropsAreAttributedToTheReporterThatLostThem() {
        GraviteeContext context = new GraviteeContext("org-" + unique(), "env-" + unique(), "domain-" + unique());
        AuditDropCounter drops = new AuditDropCounter(context, registry);

        drops.overflowed(audits(3));

        assertThat(counterFor(context).count())
                .describedAs("the domain that lost audits is identifiable from the metric alone")
                .isEqualTo(3d);
    }

    @Test
    void twoReportersDoNotShareACounter() {
        GraviteeContext noisy = new GraviteeContext("org-" + unique(), "env-" + unique(), "domain-" + unique());
        GraviteeContext quiet = new GraviteeContext("org-" + unique(), "env-" + unique(), "domain-" + unique());

        new AuditDropCounter(noisy, registry).overflowed(audits(5));
        AuditDropCounter quietDrops = new AuditDropCounter(quiet, registry);

        assertThat(counterFor(noisy).count()).isEqualTo(5d);
        assertThat(counterFor(quiet).count())
                .describedAs("a quiet domain must not inherit another domain's drops")
                .isZero();

        quietDrops.overflowed(audits(1));
        assertThat(counterFor(quiet).count()).isEqualTo(1d);
        assertThat(counterFor(noisy).count()).isEqualTo(5d);
    }

    @Test
    void rejectionsAreCountedPerAuditButReportedPerBatch() {
        GraviteeContext context = new GraviteeContext("org-" + unique(), "env-" + unique(), "domain-" + unique());
        AuditDropCounter drops = new AuditDropCounter(context, registry);

        List<BulkBatch.RejectedAudit> rejected = IntStream.range(0, 250)
                .mapToObj(i -> new BulkBatch.RejectedAudit("audit-" + i, "USER_LOGIN", "audits-2026.07.26", 400,
                        "failed to parse field [actor.id]"))
                .toList();

        drops.rejected(rejected);

        assertThat(rejectedCounterFor(context).count())
                .describedAs("every rejected audit is counted even though they are logged as one batch")
                .isEqualTo(250d);
    }

    @Test
    void anUnscopedReporterStillCounts() {
        AuditDropCounter drops = new AuditDropCounter(null, registry);

        // no context outside a domain scope: the counters must still work rather than blow up on a null tag
        drops.overflowed(audits(2));

        assertThat(registry.find("gio_dropped_audits")
                .tag("reason", "buffer_overflow").counters())
                .isNotEmpty();
    }

    private Counter counterFor(GraviteeContext context) {
        return search(context).tag("reason", "buffer_overflow").counter();
    }

    private Counter rejectedCounterFor(GraviteeContext context) {
        return search(context).tag("reason", "rejected").counter();
    }

    private RequiredSearch search(GraviteeContext context) {
        return registry.get("gio_dropped_audits")
                .tag("organization", context.getOrganizationId())
                .tag("environment", context.getEnvironmentId())
                .tag("domain", context.getDomainId());
    }

    private static List<Audit> audits(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> AuditFixtures.audit("a-domain", "USER_LOGIN", Status.SUCCESS, Instant.now()))
                .toList();
    }

    /** Counters live for the lifetime of the registry, so each test needs its own tag values. */
    private static String unique() {
        return java.util.UUID.randomUUID().toString();
    }
}
