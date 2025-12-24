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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MSSQLDialect extends AbstractDialect {

    @Override
    public String buildPagingClause(int page, int size) {
        return " ORDER BY a.timestamp DESC OFFSET " + (page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY ";
    }

    @Override
    public String buildLimitClause(int limit) {
        return " OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    protected String groupByConcatQueryParts(AuditReportableCriteria criteria, StringBuilder queryBuilder, StringBuilder whereClauseBuilder, String field) {
        String top = (criteria.size() != null && criteria.size() > 0) ? "TOP " + criteria.size() + " " : "";
        return "SELECT " + top + field + ", COUNT(DISTINCT a.id) as counter, MAX(a.timestamp) " +
                queryBuilder.toString() +
                whereClauseBuilder.toString() + " GROUP BY " + field;
    }

    @Override
    public SearchQuery buildHistogramQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder whereClauseBuilder = new StringBuilder();

        processQuery(referenceType, referenceId, criteria, queryBuilder, whereClauseBuilder, bindings, false, true);

        // No sequence generator for SQLServer, create a Virtual table using union of interval values
        Map<Long, Long> intervals = intervals(criteria);
        String query = "WITH time_buckets AS (\n" +
                intervals.keySet().stream()
                        .map(slot -> " SELECT [slot] = " + slot + ", " +
                                "[startDate] = DATEADD(MILLISECOND, " + slot + " % 1000, DATEADD(SECOND, " + slot + " / 1000, '19700101'))," +
                                "[endDate] = DATEADD(MILLISECOND, " + (slot + criteria.interval()) + " % 1000, DATEADD(SECOND, " + (slot + criteria.interval()) + " / 1000, '19700101'))")
                        .collect(Collectors.joining(" UNION "))
                + ")\n" +
                " SELECT b.slot, o.status, a.type, COUNT(o.status) as attempts " + queryBuilder.toString()
                + " RIGHT JOIN time_buckets b ON b.startDate <= a.timestamp and b.endDate >= a.timestamp "
                + whereClauseBuilder.toString() +
                " GROUP BY b.slot, o.status, a.type ORDER BY b.slot ASC, o.status, a.type";

        for (Map.Entry<String, Object> bind : bindings.entrySet()) {
            Object value = bind.getValue();
            if (value instanceof List) {
                String types = ((List<String>) value).stream().collect(Collectors.joining("','", "'", "'"));
                query = query.replaceAll(":" + bind.getKey(), types);
            } else if (value instanceof LocalDateTime) {
                query = query.replaceAll(":" + bind.getKey(), "'" + dateTimeFormatter.format((LocalDateTime) value) + "'");
            } else {
                query = query.replaceAll(":" + bind.getKey(), "'" + value + "'");
            }
        }

        return new SearchQuery(query, null, bindings);
    }

    @Override
    public String tableExists(String table, String schema) {
        return """
        SELECT COUNT(*) AS count
        FROM sys.tables t
        JOIN sys.schemas s ON t.schema_id = s.schema_id
        WHERE LOWER(t.name) = LOWER('%s')
          AND LOWER(s.name) = LOWER('%s')
        """.formatted(table, schema);
    }

    @Override
    public String checkForeignKeyExists(String tableName, String constraintName, String schema) {
        return """
                SELECT COUNT(*) AS count
                FROM sys.foreign_keys fk
                JOIN sys.tables t ON fk.parent_object_id = t.object_id
                JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE LOWER(fk.name) = LOWER('%s')
                  AND LOWER(t.name) = LOWER('%s')
                  AND LOWER(s.name) = LOWER('%s')
                """.formatted(constraintName, tableName, schema);
    }
}
