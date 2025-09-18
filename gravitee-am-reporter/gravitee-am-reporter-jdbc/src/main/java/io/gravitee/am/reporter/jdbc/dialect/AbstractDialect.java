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
package io.gravitee.am.reporter.jdbc.dialect;

import com.google.common.base.CaseFormat;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.reactivex.rxjava3.core.Single;
import lombok.Getter;
import lombok.Setter;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public abstract class AbstractDialect implements DialectHelper {

    private static final String STATUS = "status";
    private static final String SLOT = "slot";
    private static final String ATTEMPTS = "attempts";
    private static final String TYPE = "type";
    private String auditsTable;
    private String auditAccessPointsTable;
    private String auditOutcomesTable;
    private String auditEntitiesTable;

    private static final String INNER_JOIN = " INNER JOIN ";
    private static final String AUDIT_OUTCOME_JOIN_CONDITION = " o ON a.id = o.audit_id ";
    private static final String AUDIT_ENTITIES_JOIN_CONDITION = " e ON a.id = e.audit_id ";
    private static final String AUDIT_ACCESS_POINT_JOIN_CONDITION = " p ON a.id = p.audit_id ";

    protected final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ChronoUnit convertToChronoUnit(long millisecondsInterval) {
        if (millisecondsInterval >= 0 && millisecondsInterval < 60 * 1000) {
            return ChronoUnit.SECONDS;
        } else if (millisecondsInterval >= 60 * 1000 && millisecondsInterval < 60 * 60 * 1000) {
            return ChronoUnit.MINUTES;
        } else if (millisecondsInterval >= 60 * 60 * 1000 && millisecondsInterval < 24 * 60 * 60 * 1000) {
            return ChronoUnit.HOURS;
        } else {
            return ChronoUnit.DAYS;
        }
    }

    public static Map<Long, Long> intervals(AuditReportableCriteria criteria) {
        ChronoUnit unit = convertToChronoUnit(criteria.interval());
        Instant startDate = Instant.ofEpochMilli(criteria.from()).truncatedTo(unit);
        Instant endDate = Instant.ofEpochMilli(criteria.to()).truncatedTo(unit);

        Map<Long, Long> intervals = new TreeMap<>();
        intervals.put(startDate.toEpochMilli(), 0L);
        while (startDate.isBefore(endDate)) {
            startDate = startDate.plus(criteria.interval(), ChronoUnit.MILLIS);
            intervals.put(startDate.toEpochMilli(), 0L);
        }
        return intervals;
    }

    @Override
    public String tableExists(String table, String schema) {
            return "select 1 from information_schema.tables where table_name = '"+table+"'";
    }

    @Override
    public SearchQuery buildGroupByQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder whereClauseBuilder = new StringBuilder();

        processQuery(referenceType, referenceId, criteria, queryBuilder, whereClauseBuilder, bindings, true, false);

        String field = "a." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, criteria.field()); // default to audits table
        if (criteria.field().contains(".")) {
            String[] elt = criteria.field().split("\\.");
            field = switch (elt[0].toLowerCase()) {
                case "outcome" -> "o." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                case "target", "actor" -> "e." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                case "accesspoint" -> "p." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                default -> field;
            };
        }

        String query = groupByConcatQueryParts(criteria, queryBuilder, whereClauseBuilder, field);

        return new SearchQuery(query, null, bindings);
    }

    protected String groupByConcatQueryParts(AuditReportableCriteria criteria, StringBuilder queryBuilder, StringBuilder whereClauseBuilder, String field) {
        String limit = (criteria.size() != null && criteria.size() > 0) ? (" LIMIT " + (criteria.size() == null ? 50 : + criteria.size()) + " "): " ";
        return "SELECT " + field + ", COUNT(DISTINCT a.id) as counter " +
                queryBuilder.toString() +
                whereClauseBuilder.toString() + " GROUP BY " + field + " " +
                limit;
    }

    @Override
    public SearchQuery buildSearchQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder whereClauseBuilder = new StringBuilder();

        processQuery(referenceType, referenceId, criteria, queryBuilder, whereClauseBuilder, bindings, false, false);

        String query = "SELECT DISTINCT a.* " + queryBuilder.toString() + whereClauseBuilder.toString();
        String count = "SELECT count(DISTINCT a.id) " + queryBuilder.toString() + whereClauseBuilder.toString();

        return new SearchQuery(query, count, bindings);
    }

    protected void processQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria,
                                StringBuilder queryBuilder, StringBuilder whereClauseBuilder, Map<String, Object> bindings, boolean joinBasedOnField, boolean joinOnOutcomes) {

        boolean outcomeJoin = false;
        boolean entitiesJoin = false;

        queryBuilder.append("FROM ").append(auditsTable).append(" a ");

        whereClauseBuilder.append(" WHERE a.reference_type = :refType AND a.reference_id = :refId");
        bindings.put("refType", referenceType.name());
        bindings.put("refId", referenceId);

        // event types
        if (criteria.types() != null && !criteria.types().isEmpty()) {
            whereClauseBuilder.append(" AND a.type in ( :types )");
            bindings.put("types", criteria.types());
        }

        // event status
        if (criteria.status() != null && !criteria.status().isEmpty()) {
            queryBuilder.append(INNER_JOIN).append(auditOutcomesTable).append(AUDIT_OUTCOME_JOIN_CONDITION);
            outcomeJoin = true;

            whereClauseBuilder.append(" AND o.status = :status");
            bindings.put(STATUS, criteria.status());
        }

        // event user
        if (criteria.user() != null && !criteria.user().isEmpty()) {
            queryBuilder.append(INNER_JOIN).append(auditEntitiesTable).append(AUDIT_ENTITIES_JOIN_CONDITION);
            entitiesJoin = true;

            whereClauseBuilder.append(" AND e.alternative_id = :user");
            bindings.put("user", criteria.user());
        }

        // event user technical ID
        if (criteria.userId() != null && !criteria.userId().isEmpty()) {
            queryBuilder.append(INNER_JOIN).append(auditEntitiesTable).append(AUDIT_ENTITIES_JOIN_CONDITION);
            entitiesJoin = true;

            whereClauseBuilder.append(" AND e.id = :user");
            bindings.put("user", criteria.userId());
        }

        // time range
        if (criteria.from() != 0 && criteria.to() != 0) {

            whereClauseBuilder.append(" AND a.timestamp >= :from AND a.timestamp <= :to ");
            bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.from()), ZoneId.of(ZoneOffset.UTC.getId())));
            bindings.put("to", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.to()), ZoneId.of(ZoneOffset.UTC.getId())));

        } else {
            if (criteria.from() != 0) {
                whereClauseBuilder.append(" AND a.timestamp >= :from ");
                bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.from()), ZoneId.of(ZoneOffset.UTC.getId())));
            }

            if (criteria.to() != 0) {
                whereClauseBuilder.append(" AND a.timestamp <= :to ");
                bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.to()), ZoneId.of(ZoneOffset.UTC.getId())));
            }
        }

        if (criteria.accessPointId() != null && !criteria.accessPointId().isEmpty()) {
            queryBuilder.append(INNER_JOIN).append(auditAccessPointsTable).append(AUDIT_ACCESS_POINT_JOIN_CONDITION);
            entitiesJoin = true;

            whereClauseBuilder.append(" AND p.id = :accessPointId");
            bindings.put("accessPointId", criteria.accessPointId());
        }

        if (joinBasedOnField && criteria.field() != null && criteria.field().contains(".")) {
            String[] split = criteria.field().split("\\.");
            String auditFieldName = split[0];
            if ("outcome".equalsIgnoreCase(auditFieldName) && !outcomeJoin) {
                queryBuilder.append(INNER_JOIN).append(auditOutcomesTable).append(AUDIT_OUTCOME_JOIN_CONDITION);
            } else if ("actor".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder.append(INNER_JOIN).append(auditEntitiesTable).append(AUDIT_ENTITIES_JOIN_CONDITION);
            } else if ("target".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder.append(INNER_JOIN).append(auditEntitiesTable).append(AUDIT_ENTITIES_JOIN_CONDITION);
            } else if ("accessPoint".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder.append(INNER_JOIN).append(auditAccessPointsTable).append(AUDIT_ACCESS_POINT_JOIN_CONDITION);
            }
        }

        if (joinOnOutcomes && !outcomeJoin ) {
            queryBuilder.append(INNER_JOIN).append(auditOutcomesTable).append(AUDIT_OUTCOME_JOIN_CONDITION);
        }
    }

    public String buildPagingClause(int page, int size) {
        return " ORDER BY a.timestamp DESC LIMIT " + size + " OFFSET " + (page * size);
    }

    @Override
    public Single<List<Map<String, Object>>> buildAndProcessHistogram(DatabaseClient dbClient, ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        SearchQuery searchQuery = buildHistogramQuery(referenceType, referenceId, criteria);
        DatabaseClient.GenericExecuteSpec histogram = dbClient.sql(searchQuery.getQuery());
        return fluxToFlowable(histogram.map(this::toHistogramSlotValue).all()).toList();
    }

    protected Map<String, Object> toHistogramSlotValue(Row row, RowMetadata rowMetadata) {
        return Map.of(SLOT, row.get(SLOT), STATUS, row.get(STATUS), ATTEMPTS, row.get(ATTEMPTS), TYPE, row.get(TYPE));
    }

}
