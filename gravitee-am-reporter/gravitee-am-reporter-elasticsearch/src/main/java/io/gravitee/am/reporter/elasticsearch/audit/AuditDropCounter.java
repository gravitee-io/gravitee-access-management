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

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.node.monitoring.metrics.Metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.CustomLog;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * The reporter is at-most-once: under a sustained outage audits are dropped rather than allowed to
 * exhaust the node. That loss must never be silent, so every drop is logged with its reason and
 * per-type counts, and counted on the node's metrics registry so it can be alerted on.
 *
 * @author GraviteeSource Team
 */
@CustomLog
final class AuditDropCounter {

    private static final String METRIC = "gio_dropped_audits";
    private static final String TAG_REASON = "reason";
    private static final String TAG_ORGANIZATION = "organization";
    private static final String TAG_ENVIRONMENT = "environment";
    private static final String TAG_DOMAIN = "domain";

    /** Enough rejections to show the shape of the problem without reproducing the whole batch. */
    private static final int EXEMPLAR_REJECTIONS = 3;

    private final GraviteeContext context;
    private final MeterRegistry registry;

    private final Counter overflow;
    private final Counter retriesExhausted;
    private final Counter rejected;
    private final Counter notWritable;
    private final Counter notAccepted;
    private final Counter unserializable;

    /**
     * @param context the reference this reporter serves, used to tag its counters. An install runs one
     *                reporter per domain, so without it every domain's drops land on one undifferentiated
     *                series and an alert cannot say which domain is losing audits. Null outside a domain
     *                scope, in which case the counters are left untagged rather than mislabelled.
     */
    AuditDropCounter(GraviteeContext context) {
        this(context, Metrics.getDefaultRegistry());
    }

    AuditDropCounter(GraviteeContext context, MeterRegistry registry) {
        this.context = context;
        this.registry = registry;
        this.overflow = counter("buffer_overflow");
        this.retriesExhausted = counter("retries_exhausted");
        this.rejected = counter("rejected");
        this.notWritable = counter("not_writable");
        this.notAccepted = counter("reporter_stopping");
        this.unserializable = counter("unserializable");
    }

    /** The backlog was full, so the oldest pending audits were evicted to keep memory bounded. */
    void overflowed(List<Audit> audits) {
        overflow.increment(audits.size());
        log.error("Dropped {} audits: the Elasticsearch backlog is full and the oldest pending batch was evicted. " +
                        "Elasticsearch is not keeping up or is unreachable. Per type: {}",
                audits.size(), BulkBatch.countsByType(audits));
    }

    /** Every retry was used up and the batch still had not been acknowledged. */
    void retriesExhausted(BulkBatch batch, Throwable cause) {
        retriesExhausted.increment(batch.size());
        log.error("Dropped {} audits: Elasticsearch did not acknowledge them within the retry budget. Per type: {}",
                batch.size(), batch.countsByType(), cause);
    }

    /**
     * The audits could not be turned into bulk documents at all. {@link BulkBatch} has already logged
     * each one with the cause that no other drop path has, so this only has to make sure the drop
     * still reaches the metric operators alert on.
     */
    void unserializable(int count) {
        unserializable.increment(count);
    }

    /**
     * Elasticsearch refused these documents outright. Replaying them would fail identically until the
     * underlying mapping is fixed, so they are dropped rather than retried.
     * <p>
     * Reported once for the batch. The cause of a rejection is almost always systemic — a mapping
     * conflict refuses every audit carrying the offending field — so a line per audit would emit
     * thousands of copies of the one message an operator needs to read.
     */
    void rejected(List<BulkBatch.RejectedAudit> audits) {
        rejected.increment(audits.size());

        Map<String, Long> byStatus = audits.stream()
                .collect(groupingBy(audit -> String.valueOf(audit.status()), TreeMap::new, counting()));
        String exemplars = audits.stream()
                .limit(EXEMPLAR_REJECTIONS)
                .map(audit -> "%s (%s, index %s, status %s): %s"
                        .formatted(audit.auditId(), audit.auditType(), audit.index(), audit.status(), audit.reason()))
                .collect(joining("; "));

        log.error("Dropped {} audits: Elasticsearch refused them and replaying would fail identically. " +
                        "By status: {}. First {}: {}",
                audits.size(), byStatus, Math.min(EXEMPLAR_REJECTIONS, audits.size()), exemplars);
    }

    /** The reporter never became writable — its index template could not be applied. */
    void notWritable(BulkBatch batch, Throwable cause) {
        notWritable.increment(batch.size());
        log.error("Dropped {} audits: the Elasticsearch reporter is not writable, so nothing can be indexed. " +
                "Per type: {}", batch.size(), batch.countsByType(), cause);
    }

    /** Reported after the reporter began stopping, so it was never buffered. */
    void notAccepted(String auditId) {
        notAccepted.increment();
        log.warn("Dropped audit {}: the Elasticsearch reporter is stopping and no longer accepts audits", auditId);
    }

    private Counter counter(String reason) {
        Counter.Builder builder = Counter.builder(METRIC)
                .description("Audits the Elasticsearch reporter could not deliver")
                .tag(TAG_REASON, reason);
        if (context != null) {
            builder.tag(TAG_ORGANIZATION, orEmpty(context.getOrganizationId()))
                    .tag(TAG_ENVIRONMENT, orEmpty(context.getEnvironmentId()))
                    .tag(TAG_DOMAIN, orEmpty(context.getDomainId()));
        }
        return builder.register(registry);
    }

    /** Micrometer rejects a null tag value, and an organization reporter has no domain. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
