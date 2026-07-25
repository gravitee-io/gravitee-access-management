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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.elasticsearch.model.bulk.BulkItemResponse;
import io.gravitee.elasticsearch.model.bulk.BulkResponse;
import io.gravitee.elasticsearch.model.bulk.Index;
import io.vertx.core.buffer.Buffer;
import lombok.CustomLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One serialized {@code _bulk} request, and the audits that went into it.
 *
 * @author GraviteeSource Team
 */
@CustomLog
final class BulkBatch {

    /** Server-side conditions worth another attempt; everything else in the 4xx range is a refusal. */
    private static final int TOO_MANY_REQUESTS = 429;

    /**
     * Elasticsearch quotes the offending value back in a parse failure, and an audit's values are
     * usernames, addresses and user agents. The exception type and field name lead the message and are
     * what makes it diagnosable, so keeping the head and dropping the tail bounds how much of a record
     * can reach the logs without giving up the diagnosis. It bounds the exposure rather than removing
     * it — a short enough value still fits.
     */
    static final int MAX_REASON_LENGTH = 256;

    private final String baseIndex;
    private final ObjectMapper mapper;
    private final List<Audit> audits;
    private final List<Audit> unserializable;
    private final Buffer payload;

    private BulkBatch(String baseIndex, ObjectMapper mapper, List<Audit> audits, List<Audit> unserializable, Buffer payload) {
        this.baseIndex = baseIndex;
        this.mapper = mapper;
        this.audits = audits;
        this.unserializable = unserializable;
        this.payload = payload;
    }

    /**
     * Serializes the audits into an NDJSON bulk payload. Each document is addressed by its audit id
     * in the daily index its own timestamp falls in, which makes every write idempotent: a retry or a
     * concurrent writer overwrites rather than duplicates.
     */
    static BulkBatch of(String baseIndex, ObjectMapper mapper, List<Audit> audits) {
        List<Audit> serialized = new ArrayList<>(audits.size());
        List<Audit> unserializable = new ArrayList<>();
        Buffer payload = Buffer.buffer();
        for (Audit audit : audits) {
            try {
                String index = AuditIndexNames.writeIndex(baseIndex, audit.timestamp());
                String action = mapper.writeValueAsString(Map.of("index", Map.of("_index", index, "_id", audit.getId())));
                String source = mapper.writeValueAsString(AuditConverter.toDocument(audit));
                payload.appendString(action).appendString("\n").appendString(source).appendString("\n");
                serialized.add(audit);
            } catch (Exception e) {
                // logged here because this is the only place the cause is available; the caller still
                // has to count it, so the drop reaches the metric alongside every other drop reason
                log.error("Unable to serialize audit {} for Elasticsearch, it will not be indexed", audit.getId(), e);
                unserializable.add(audit);
            }
        }
        return new BulkBatch(baseIndex, mapper, serialized, unserializable, payload);
    }

    /** Audits that could not be turned into documents at all, so were never part of the payload. */
    List<Audit> unserializable() {
        return unserializable;
    }

    Buffer payload() {
        return payload;
    }

    int size() {
        return audits.size();
    }

    boolean isEmpty() {
        return audits.isEmpty();
    }

    int bytes() {
        return payload.length();
    }

    Map<String, Long> countsByType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Audit audit : audits) {
            counts.merge(audit.getType(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Splits a bulk response per item. A response can succeed overall while refusing individual
     * documents, so anything Elasticsearch permanently rejected is handed to {@code onRejected} and
     * never replayed — replaying it would fail identically and burn the whole batch's retry budget
     * for nothing. Only genuinely retryable items come back.
     */
    BulkBatch retryable(BulkResponse response, Consumer<RejectedAudit> onRejected) {
        List<BulkItemResponse> items = response == null ? null : response.getItems();
        if (items == null) {
            // no per-item detail: treat the whole batch as unacknowledged rather than assume success
            return this;
        }

        List<Audit> retry = new ArrayList<>();
        int count = Math.min(items.size(), audits.size());
        for (int i = 0; i < count; i++) {
            Audit audit = audits.get(i);
            Index result = items.get(i).getIndex();
            Integer status = result == null ? null : result.getStatus();
            if (isSucceeded(status)) {
                continue;
            }
            if (isRetryable(status)) {
                retry.add(audit);
            } else {
                onRejected.accept(new RejectedAudit(
                        audit.getId(),
                        audit.getType(),
                        result.getIndexName(),
                        status,
                        result.getError() == null ? null : abbreviate(result.getError().getReason())));
            }
        }
        // any audit the response did not cover was never acknowledged
        for (int i = count; i < audits.size(); i++) {
            retry.add(audits.get(i));
        }
        return of(baseIndex, mapper, retry);
    }

    private static String abbreviate(String reason) {
        if (reason == null || reason.length() <= MAX_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

    private static boolean isSucceeded(Integer status) {
        return status != null && status < 400;
    }

    private static boolean isRetryable(Integer status) {
        // an unreadable status is assumed transient rather than silently dropped
        return status == null || status == TOO_MANY_REQUESTS || status >= 500;
    }

    /** An audit Elasticsearch refused outright. The record is gone; the reason is not. */
    record RejectedAudit(String auditId, String auditType, String index, Integer status, String reason) {
    }
}
