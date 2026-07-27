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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class ElasticsearchQueryBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ElasticsearchQueryBuilder queryBuilder = new ElasticsearchQueryBuilder(MAPPER);

    @Test
    void asksElasticsearchToTrackTotalsAccurately() throws Exception {
        JsonNode query = MAPPER.readTree(queryBuilder.buildSearchQuery(
                ReferenceType.DOMAIN, "domain-1", new AuditReportableCriteria.Builder().build(), 0, 10));

        assertThat(query.get("track_total_hits").asBoolean())
                .describedAs("without this the console total freezes at the default tracking limit")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessPoint.id", "type", "outcome.status"})
    void mapsTheFieldsTheAnalyticsScreensAskFor(String criteriaField) throws Exception {
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().field(criteriaField).build();

        JsonNode query = MAPPER.readTree(queryBuilder.buildGroupByQuery(ReferenceType.DOMAIN, "domain-1", criteria));

        assertThat(query.get("aggregations").get("by_field").get("terms").get("field").asText())
                .isEqualTo(criteriaField);
    }

    @Test
    void refusesToGroupByAnUnknownField() {
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().field("actor.favourite_colour").build();

        assertThatThrownBy(() -> queryBuilder.buildGroupByQuery(ReferenceType.DOMAIN, "domain-1", criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor.favourite_colour")
                .hasMessageContaining("Supported fields");
    }

    @Test
    void refusesToGroupByNothing() {
        // the analytics service's default branch never sets a field, which used to reach Elasticsearch as a 400
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().build();

        assertThatThrownBy(() -> queryBuilder.buildGroupByQuery(ReferenceType.DOMAIN, "domain-1", criteria))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsAHistogramWhoseBucketsElasticsearchWillAccept() throws Exception {
        long day = 86_400_000L;
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder()
                .from(0L + day)
                .to(day * 31)
                .interval(day)
                .types(List.of("USER_LOGIN", "USER_LOGOUT"))
                .build();

        JsonNode query = MAPPER.readTree(queryBuilder.buildDateHistogramQuery(ReferenceType.DOMAIN, "domain-1", criteria));

        assertThat(query.get("aggregations").get("by_date").get("date_histogram").get("fixed_interval").asText())
                .isEqualTo(day + "ms");
        assertThat(query.get("aggregations").get("by_date").get("aggregations"))
                .describedAs("one filter series per type and status")
                .hasSize(4);
    }

    @Test
    void refusesAHistogramThatWouldAskForMoreBucketsThanElasticsearchAllows() {
        // 90 days at one minute, across 30 audit types: millions of buckets, which Elasticsearch
        // answers with a raw aggregation error rather than anything an operator can act on
        long minute = 60_000L;
        List<String> types = IntStream.range(0, 30).mapToObj(i -> "AUDIT_TYPE_" + i).toList();
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder()
                .from(minute)
                .to(minute + 90L * 24 * 60 * minute)
                .interval(minute)
                .types(types)
                .build();

        assertThatThrownBy(() -> queryBuilder.buildDateHistogramQuery(ReferenceType.DOMAIN, "domain-1", criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buckets")
                .hasMessageContaining("Widen the interval or narrow the date range");
    }

    @Test
    void leavesAnOpenEndedHistogramToElasticsearch() {
        // no bounds means no materialized empty buckets, so there is nothing to bound up front
        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder()
                .interval(1L)
                .types(List.of("USER_LOGIN"))
                .build();

        assertThat(queryBuilder.buildDateHistogramQuery(ReferenceType.DOMAIN, "domain-1", criteria))
                .isNotEmpty();
    }
}
