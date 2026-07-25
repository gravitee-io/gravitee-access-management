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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Builds the JSON query/aggregation bodies sent to Elasticsearch from an {@link AuditReportableCriteria}.
 * Field paths mirror the {@link io.gravitee.am.reporter.elasticsearch.audit.model.AuditDocument} layout.
 *
 * @author GraviteeSource Team
 */
public class ElasticsearchQueryBuilder {

    static final String FIELD_REFERENCE_TYPE = "referenceType";
    static final String FIELD_REFERENCE_ID = "referenceId";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String FIELD_TYPE = "type";
    static final String FIELD_STATUS = "outcome.status";
    static final String FIELD_ACTOR = "actor.alternativeId";
    static final String FIELD_ACTOR_ID = "actor.id";
    static final String FIELD_TARGET = "target.alternativeId";
    static final String FIELD_TARGET_ID = "target.id";
    static final String FIELD_ACCESS_POINT_ID = "accessPoint.id";

    static final String AGG_BY_DATE = "by_date";
    static final String AGG_BY_FIELD = "by_field";

    /**
     * Elasticsearch refuses {@code from + size} past this by default, so paging beyond it fails
     * rather than returning wrong data.
     */
    static final int MAX_RESULT_WINDOW = 10_000;

    /**
     * Criteria field names are mapped explicitly rather than passed straight through, so a field name
     * this reporter does not know fails with a clear message instead of silently returning empty
     * buckets or taking a 400 from Elasticsearch.
     */
    private static final Map<String, String> GROUP_BY_FIELDS = Map.of(
            "accessPoint.id", FIELD_ACCESS_POINT_ID,
            "type", FIELD_TYPE,
            "outcome.status", FIELD_STATUS);

    private final ObjectMapper mapper;

    public ElasticsearchQueryBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static String histogramKey(String type, Status status) {
        return (type + "_" + status.name()).toLowerCase();
    }

    public String buildSearchQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        ObjectNode root = mapper.createObjectNode();
        root.put("from", (long) size * page);
        root.put("size", size);
        // without this the total silently caps at 10 000 while the database reporters return the truth
        root.put("track_total_hits", true);
        ArrayNode sort = root.putArray("sort");
        sort.addObject().putObject(FIELD_TIMESTAMP).put("order", "desc");
        root.set("query", boolFilter(referenceType, referenceId, criteria));
        return root.toString();
    }

    public String buildFindByIdQuery(ReferenceType referenceType, String referenceId, String id) {
        ObjectNode root = mapper.createObjectNode();
        root.put("size", 1);
        ObjectNode bool = mapper.createObjectNode();
        ArrayNode filter = bool.putObject("bool").putArray("filter");
        filter.add(term(FIELD_REFERENCE_TYPE, referenceType.name()));
        filter.add(term(FIELD_REFERENCE_ID, referenceId));
        filter.addObject().putObject("ids").putArray("values").add(id);
        root.set("query", bool);
        return root.toString();
    }

    public String buildCountQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        ObjectNode root = mapper.createObjectNode();
        root.set("query", boolFilter(referenceType, referenceId, criteria));
        return root.toString();
    }

    public String buildGroupByQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        ObjectNode root = mapper.createObjectNode();
        root.put("size", 0);
        root.set("query", boolFilter(referenceType, referenceId, criteria));
        ObjectNode terms = root.putObject("aggregations").putObject(AGG_BY_FIELD).putObject("terms");
        terms.put("field", groupByField(criteria.field()));
        terms.put("size", (criteria.size() != null && criteria.size() > 0) ? criteria.size() : 10);
        return root.toString();
    }

    public String buildDateHistogramQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        ObjectNode root = mapper.createObjectNode();
        root.put("size", 0);
        root.set("query", boolFilter(referenceType, referenceId, criteria));

        ObjectNode byDate = root.putObject("aggregations").putObject(AGG_BY_DATE);
        ObjectNode dateHisto = byDate.putObject("date_histogram");
        dateHisto.put("field", FIELD_TIMESTAMP);
        long interval = criteria.interval() > 0 ? criteria.interval() : 86_400_000L;
        dateHisto.put("fixed_interval", interval + "ms");
        dateHisto.put("min_doc_count", 0);
        ObjectNode bounds = dateHisto.putObject("extended_bounds");
        bounds.put("min", criteria.from());
        bounds.put("max", criteria.to());

        // one filter sub-aggregation per {type}_{status} series
        ObjectNode subAggs = byDate.putObject("aggregations");
        if (criteria.types() != null) {
            for (String type : criteria.types()) {
                for (Status status : new Status[]{Status.SUCCESS, Status.FAILURE}) {
                    ObjectNode boolFilter = subAggs
                            .putObject(histogramKey(type, status))
                            .putObject("filter")
                            .putObject("bool");
                    ArrayNode filters = boolFilter.putArray("filter");
                    filters.add(term(FIELD_TYPE, type));
                    filters.add(term(FIELD_STATUS, status.name()));
                }
            }
        }
        return root.toString();
    }

    private ObjectNode boolFilter(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        ObjectNode query = mapper.createObjectNode();
        ArrayNode filter = query.putObject("bool").putArray("filter");

        filter.add(term(FIELD_REFERENCE_TYPE, referenceType.name()));
        filter.add(term(FIELD_REFERENCE_ID, referenceId));

        if (criteria.types() != null && !criteria.types().isEmpty()) {
            ArrayNode values = mapper.createArrayNode();
            criteria.types().forEach(values::add);
            filter.addObject().putObject("terms").set(FIELD_TYPE, values);
        }

        if (StringUtils.hasLength(criteria.status())) {
            filter.add(term(FIELD_STATUS, criteria.status()));
        }

        if (StringUtils.hasLength(criteria.user())) {
            filter.add(shouldMatch(FIELD_ACTOR, FIELD_TARGET, criteria.user()));
        }

        if (StringUtils.hasLength(criteria.userId())) {
            filter.add(shouldMatch(FIELD_ACTOR_ID, FIELD_TARGET_ID, criteria.userId()));
        }

        if (criteria.from() != 0 || criteria.to() != 0) {
            ObjectNode range = mapper.createObjectNode();
            ObjectNode bounds = range.putObject("range").putObject(FIELD_TIMESTAMP);
            if (criteria.from() != 0) {
                bounds.put("gte", criteria.from());
            }
            if (criteria.to() != 0) {
                bounds.put("lte", criteria.to());
            }
            filter.add(range);
        }

        if (StringUtils.hasLength(criteria.accessPointId())) {
            filter.add(term(FIELD_ACCESS_POINT_ID, criteria.accessPointId()));
        }

        return query;
    }

    private static String groupByField(String criteriaField) {
        String field = criteriaField == null ? null : GROUP_BY_FIELDS.get(criteriaField);
        if (field == null) {
            throw new IllegalArgumentException("Audits cannot be grouped by '%s'. Supported fields: %s"
                    .formatted(criteriaField, GROUP_BY_FIELDS.keySet()));
        }
        return field;
    }

    private ObjectNode term(String field, String value) {
        ObjectNode node = mapper.createObjectNode();
        node.putObject("term").put(field, value);
        return node;
    }

    private ObjectNode shouldMatch(String firstField, String secondField, String value) {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode bool = node.putObject("bool");
        ArrayNode should = bool.putArray("should");
        should.add(term(firstField, value));
        should.add(term(secondField, value));
        bool.put("minimum_should_match", 1);
        return node;
    }
}
