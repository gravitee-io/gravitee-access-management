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
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.elasticsearch.AuditFixtures;
import io.gravitee.elasticsearch.model.bulk.BulkResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bulk response can succeed overall while refusing individual documents. Getting the partition
 * wrong either loses good audits or replays a document that will never be accepted.
 *
 * @author GraviteeSource Team
 */
class BulkBatchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void addressesEachAuditByIdInItsOwnDailyIndex() {
        Audit audit = AuditFixtures.audit("domain-1", "USER_CREATED", Status.SUCCESS, Instant.parse("2026-07-25T13:45:00Z"));

        BulkBatch batch = BulkBatch.of("gravitee-audit", MAPPER, List.of(audit));

        assertThat(batch.payload().toString())
                .contains("\"_index\":\"gravitee-audit-2026.07.25\"")
                .contains("\"_id\":\"" + audit.getId() + "\"");
    }

    @Test
    void keepsNothingToRetryWhenEveryItemSucceeded() {
        BulkBatch batch = batchOf(2);
        List<BulkBatch.RejectedAudit> rejected = new ArrayList<>();

        BulkBatch remaining = batch.retryable(response(201, 201), rejected::add);

        assertThat(remaining.isEmpty()).isTrue();
        assertThat(rejected).isEmpty();
    }

    @Test
    void retriesOnlyTheItemsWorthRetrying() {
        BulkBatch batch = batchOf(4);
        List<BulkBatch.RejectedAudit> rejected = new ArrayList<>();

        BulkBatch remaining = batch.retryable(response(201, 429, 503, 201), rejected::add);

        assertThat(remaining.size()).isEqualTo(2);
        assertThat(rejected).isEmpty();
    }

    @Test
    void neverReplaysADocumentElasticsearchRefused() {
        BulkBatch batch = batchOf(3);
        List<BulkBatch.RejectedAudit> rejected = new ArrayList<>();

        BulkBatch remaining = batch.retryable(response(201, 400, 201), rejected::add);

        assertThat(remaining.isEmpty())
                .describedAs("a permanently rejected document must not consume the batch's retry budget")
                .isTrue();
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).status()).isEqualTo(400);
        assertThat(rejected.get(0).reason()).contains("failed to parse field");
        assertThat(rejected.get(0).index()).isEqualTo("gravitee-audit-2026.07.25");
    }

    @Test
    void reportsTheRejectedAuditsOwnIdAndTypeSoItCanBeFoundInTheLogs() {
        Audit audit = AuditFixtures.audit("domain-1", "USER_CREATED", Status.SUCCESS, Instant.parse("2026-07-25T13:45:00Z"));
        BulkBatch batch = BulkBatch.of("gravitee-audit", MAPPER, List.of(audit));
        List<BulkBatch.RejectedAudit> rejected = new ArrayList<>();

        batch.retryable(response(400), rejected::add);

        assertThat(rejected.get(0).auditId()).isEqualTo(audit.getId());
        assertThat(rejected.get(0).auditType()).isEqualTo("USER_CREATED");
    }

    @Test
    void treatsAResponseWithNoItemDetailAsUnacknowledged() {
        BulkBatch batch = batchOf(2);

        BulkBatch remaining = batch.retryable(new BulkResponse(), audit -> {
        });

        assertThat(remaining.size()).isEqualTo(2);
    }

    @Test
    void treatsItemsTheResponseNeverCoveredAsUnacknowledged() {
        BulkBatch batch = batchOf(3);

        BulkBatch remaining = batch.retryable(response(201), audit -> {
        });

        assertThat(remaining.size()).isEqualTo(2);
    }

    @Test
    void countsWhatWasLostByAuditType() {
        List<Audit> audits = List.of(
                AuditFixtures.audit("domain-1", "USER_CREATED", Status.SUCCESS, Instant.now()),
                AuditFixtures.audit("domain-1", "USER_LOGIN", Status.SUCCESS, Instant.now()),
                AuditFixtures.audit("domain-1", "USER_LOGIN", Status.FAILURE, Instant.now()));

        assertThat(BulkBatch.of("gravitee-audit", MAPPER, audits).countsByType())
                .containsEntry("USER_CREATED", 1L)
                .containsEntry("USER_LOGIN", 2L);
    }

    private static BulkBatch batchOf(int count) {
        List<Audit> audits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            audits.add(AuditFixtures.audit("domain-1", "USER_CREATED", Status.SUCCESS, Instant.parse("2026-07-25T13:45:00Z")));
        }
        return BulkBatch.of("gravitee-audit", MAPPER, audits);
    }

    private static final String PARSE_FAILURE =
            ",\"error\":{\"type\":\"document_parsing_exception\",\"reason\":\"failed to parse field [actor.attributes.probe]\"}";

    private static BulkResponse response(int... statuses) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                items.append(",");
            }
            items.append("{\"index\":{\"_index\":\"gravitee-audit-2026.07.25\",\"_id\":\"doc-").append(i)
                    .append("\",\"status\":").append(statuses[i])
                    .append(statuses[i] == 400 ? PARSE_FAILURE : "")
                    .append("}}");
        }
        String json = "{\"took\":3,\"errors\":true,\"items\":[" + items + "]}";
        try {
            return MAPPER.readValue(json, BulkResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable fixture: " + json, e);
        }
    }
}
