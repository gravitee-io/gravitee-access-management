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

import io.gravitee.node.monitoring.metrics.Metrics;
import io.micrometer.core.instrument.Counter;
import lombok.CustomLog;

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

    private final Counter overflow = counter("buffer_overflow");
    private final Counter retriesExhausted = counter("retries_exhausted");
    private final Counter rejected = counter("rejected");
    private final Counter notWritable = counter("not_writable");
    private final Counter notAccepted = counter("reporter_stopping");
    private final Counter unserializable = counter("unserializable");

    /** The backlog was full, so the oldest pending batch was evicted to keep memory bounded. */
    void overflowed(BulkBatch batch) {
        overflow.increment(batch.size());
        log.error("Dropped {} audits: the Elasticsearch backlog is full and the oldest pending batch was evicted. " +
                        "Elasticsearch is not keeping up or is unreachable. Per type: {}",
                batch.size(), batch.countsByType());
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
     * Elasticsearch refused this document outright. Replaying it would fail identically until the
     * underlying mapping is fixed, so it is dropped rather than retried.
     */
    void rejected(BulkBatch.RejectedAudit audit) {
        rejected.increment();
        log.error("Dropped audit {} of type {}: Elasticsearch refused it for index {} with status {} — {}",
                audit.auditId(), audit.auditType(), audit.index(), audit.status(), audit.reason());
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

    private static Counter counter(String reason) {
        return Counter.builder(METRIC)
                .description("Audits the Elasticsearch reporter could not deliver")
                .tag(TAG_REASON, reason)
                .register(Metrics.getDefaultRegistry());
    }
}
