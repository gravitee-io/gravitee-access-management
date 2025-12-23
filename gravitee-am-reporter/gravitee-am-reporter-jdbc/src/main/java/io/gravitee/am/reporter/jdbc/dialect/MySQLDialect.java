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
import io.reactivex.rxjava3.core.Single;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MySQLDialect extends AbstractDialect {

    @Override
    public Single<List<Map<String, Object>>>  buildAndProcessHistogram(DatabaseClient dbClient, ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder whereClauseBuilder = new StringBuilder();

        processQuery(referenceType, referenceId, criteria, queryBuilder, whereClauseBuilder, bindings, false, true);

        // Sequence Generator exist only since MySQL 8.x
        // process multiple queries to build the result
        Map<Long, Long> intervals = intervals(criteria);
        return fluxToFlowable(Flux.fromIterable(intervals.keySet()).flatMap(slot -> {
            String beginSlot = dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(slot), ZoneId.of(ZoneOffset.UTC.getId())));
            String endSlot = dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(slot + criteria.interval()), ZoneId.of(ZoneOffset.UTC.getId())));
            String query =
                    " SELECT " + slot + " as slot, o.status, a.type, COUNT(o.status) as attempts " + queryBuilder
                            + whereClauseBuilder +
                            " AND '" + beginSlot + "' <= a.timestamp and '" + endSlot + "' >= a.timestamp " +
                            " GROUP BY o.status, a.type ORDER BY o.status ";

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

            return dbClient.sql(query).map(this::toHistogramSlotValue).all();
        })).toList();
    }

    @Override
    public SearchQuery buildHistogramQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
       throw new IllegalStateException("Not implemented for MySQL");
    }
    @Override
    public String tableExists(String table, String schema) {
    return """
            SELECT COUNT(*) AS count
            FROM information_schema.tables
            WHERE LOWER(table_name) = LOWER('%s')
              AND LOWER(table_schema) = LOWER(DATABASE())
            """.formatted(table);
    }

    @Override
    public String checkForeignKeyExists(String tableName, String constraintName, String schema) {
        // MySQL uses DATABASE() instead of schema parameter
        return """
                SELECT COUNT(*) AS count
                FROM information_schema.table_constraints
                WHERE LOWER(constraint_name) = LOWER('%s')
                  AND LOWER(table_name) = LOWER('%s')
                  AND LOWER(table_schema) = LOWER(DATABASE())
                  AND constraint_type = 'FOREIGN KEY'
                """.formatted(constraintName, tableName);
    }
}
