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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PostgresqlDialect extends AbstractDialect {

    @Override
    public SearchQuery buildHistogramQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria) {
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();
        StringBuilder whereClauseBuilder = new StringBuilder();

        processQuery(referenceType, referenceId, criteria, queryBuilder, whereClauseBuilder, bindings, false, true);

        ChronoUnit unit = AbstractDialect.convertToChronoUnit(criteria.interval());
        String startDate = dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.from()).truncatedTo(unit), ZoneId.of(ZoneOffset.UTC.getId())));
        String endDate = dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(criteria.to()).truncatedTo(unit), ZoneId.of(ZoneOffset.UTC.getId())));

        // Use sequence generator to create Virtual table
        String query = "WITH time_buckets AS (\n" +
                "                SELECT\n" +
                "                (EXTRACT(EPOCH FROM bucket))*1000 as slot,\n" +
                "                (bucket) AS startDate,\n" +
                "                (bucket + '" + criteria.interval() + " milliseconds'::interval) AS endDate\n" +
                "        FROM generate_series\n" +
                "        ( '" + startDate + "'::timestamp\n" +
                "                , '" + endDate + "'::timestamp\n" +
                "                , '" + criteria.interval() + " milliseconds'::interval) bucket\n" +
                ")\n" +
                " SELECT b.slot, o.status, COUNT(o.status) as attempts " + queryBuilder.toString()
                + " RIGHT JOIN time_buckets b ON b.startDate <= a.timestamp and b.endDate >= a.timestamp "
                + whereClauseBuilder.toString() +
                " GROUP BY b.slot, o.status ORDER BY b.slot ASC, o.status ";

        for (Map.Entry<String, Object> bind : bindings.entrySet()) {
            Object value = bind.getValue();
            if (value instanceof List) {
                String types = ((List<String>)value).stream().collect(Collectors.joining("','", "'", "'"));
                query = query.replaceAll(":"+bind.getKey(), types);
            } else if (value instanceof LocalDateTime) {
                query = query.replaceAll(":"+bind.getKey(), "'" + dateTimeFormatter.format((LocalDateTime)value) + "'");
            } else {
                query = query.replaceAll(":"+bind.getKey(), "'" + value + "'");
            }
        }

        return new SearchQuery(query, null, bindings);
    }

}
