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
public abstract class AbstractDialect implements DialectHelper {

    private String auditsTable;
    private String auditAccessPointsTable;
    private String auditOutcomesTable;
    private String auditEntitiesTable;

    protected final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String getAuditsTable() {
        return auditsTable;
    }

    public void setAuditsTable(String auditsTable) {
        this.auditsTable = auditsTable;
    }

    public void setAuditAccessPointsTable(String auditAccessPointsTable) {
        this.auditAccessPointsTable = auditAccessPointsTable;
    }

    public void setAuditOutcomesTable(String auditOutcomesTable) {
        this.auditOutcomesTable = auditOutcomesTable;
    }

    public void setAuditEntitiesTable(String auditEntitiesTable) {
        this.auditEntitiesTable = auditEntitiesTable;
    }

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
        intervals.put(startDate.toEpochMilli(), 0l);
        while (startDate.isBefore(endDate)) {
            startDate = startDate.plus(criteria.interval(), ChronoUnit.MILLIS);
            intervals.put(startDate.toEpochMilli(), 0l);
        }
        return intervals;
    }

    @Override
    public String tableExists(String table) {
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
            switch (elt[0].toLowerCase()) {
                case "outcome":
                    field = "o." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                    break;
                case "target":
                case "actor":
                    field = "e." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                    break;
                case "accesspoint":
                    field = "p." + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, elt[1]);
                    break;
            }
        }

        String query = groupByConcatQueryParts(criteria, queryBuilder, whereClauseBuilder, field);

        return new SearchQuery(query, null, bindings);
    }

    protected String groupByConcatQueryParts(AuditReportableCriteria criteria, StringBuilder queryBuilder, StringBuilder whereClauseBuilder, String field) {
        String query = "SELECT " + field + ", COUNT(DISTINCT a.id) as counter " +
                queryBuilder.toString() +
                whereClauseBuilder.toString() + " GROUP BY " + field + " " +
                " LIMIT " + (criteria.size() == null ? 50 : + criteria.size()) + " ";
        return query;
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

        queryBuilder = queryBuilder.append("FROM "+auditsTable+" a ");

        whereClauseBuilder = whereClauseBuilder.append(" WHERE a.reference_type = :refType AND a.reference_id = :refId");
        bindings.put("refType", referenceType.name());
        bindings.put("refId", referenceId);

        // event types
        if (criteria.types() != null && !criteria.types().isEmpty()) {
            whereClauseBuilder = whereClauseBuilder.append(" AND a.type in ( :types )");
            bindings.put("types", criteria.types());
        }

        // event status
        if (criteria.status() != null && !criteria.status().isEmpty()) {
            queryBuilder = queryBuilder.append(" INNER JOIN "+auditOutcomesTable+" o ON a.id = o.audit_id ");
            outcomeJoin = true;

            whereClauseBuilder = whereClauseBuilder.append(" AND o.status = :status");
            bindings.put("status", criteria.status());
        }

        // event user
        if (criteria.user() != null && !criteria.user().isEmpty()) {
            queryBuilder = queryBuilder.append(" INNER JOIN "+auditEntitiesTable+" e ON a.id = e.audit_id ");
            entitiesJoin = true;

            whereClauseBuilder = whereClauseBuilder.append(" AND e.alternative_id = :user");
            bindings.put("user", criteria.user());
        }

        // event user technical ID
        if (criteria.userId() != null && !criteria.userId().isEmpty()) {
            queryBuilder = queryBuilder.append(" INNER JOIN "+auditEntitiesTable+" e ON a.id = e.audit_id ");
            entitiesJoin = true;

            whereClauseBuilder = whereClauseBuilder.append(" AND e.id = :user");
            bindings.put("user", criteria.userId());
        }

        // time range
        if (criteria.from() != 0 && criteria.to() != 0) {

            whereClauseBuilder = whereClauseBuilder.append(" AND a.timestamp >= :from AND a.timestamp <= :to ");
            bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.from()), ZoneId.of(ZoneOffset.UTC.getId())));
            bindings.put("to", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.to()), ZoneId.of(ZoneOffset.UTC.getId())));

        } else {
            if (criteria.from() != 0) {
                whereClauseBuilder = whereClauseBuilder.append(" AND a.timestamp >= :from ");
                bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.from()), ZoneId.of(ZoneOffset.UTC.getId())));
            }

            if (criteria.to() != 0) {
                whereClauseBuilder = whereClauseBuilder.append(" AND a.timestamp <= :to ");
                bindings.put("from", LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.to()), ZoneId.of(ZoneOffset.UTC.getId())));
            }
        }

        if (criteria.accessPointId() != null && !criteria.accessPointId().isEmpty()) {
            queryBuilder = queryBuilder.append(" INNER JOIN "+auditAccessPointsTable+" p ON a.id = p.audit_id ");
            entitiesJoin = true;

            whereClauseBuilder = whereClauseBuilder.append(" AND p.id = :accessPointId");
            bindings.put("accessPointId", criteria.accessPointId());
        }

        if (joinBasedOnField && criteria.field() != null && criteria.field().contains(".")) {
            String[] split = criteria.field().split("\\.");
            String auditFieldName = split[0];
            if ("outcome".equalsIgnoreCase(auditFieldName) && !outcomeJoin) {
                queryBuilder = queryBuilder.append(" INNER JOIN " + auditOutcomesTable + " o ON a.id = o.audit_id ");
            } else if ("actor".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder = queryBuilder.append(" INNER JOIN " + auditEntitiesTable + " e ON a.id = e.audit_id ");
            } else if ("target".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder = queryBuilder.append(" INNER JOIN " + auditEntitiesTable + " e ON a.id = e.audit_id ");
            } else if ("accessPoint".equalsIgnoreCase(auditFieldName) && !entitiesJoin) {
                queryBuilder = queryBuilder.append(" INNER JOIN "+auditAccessPointsTable+" p ON a.id = p.audit_id ");
            }
        }

        if (joinOnOutcomes && !outcomeJoin ) {
            queryBuilder = queryBuilder.append(" INNER JOIN "+auditOutcomesTable+" o ON a.id = o.audit_id ");
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
        return Map.of("slot", row.get("slot"), "status", row.get("status"), "attempts", row.get("attempts"));
    }

}
