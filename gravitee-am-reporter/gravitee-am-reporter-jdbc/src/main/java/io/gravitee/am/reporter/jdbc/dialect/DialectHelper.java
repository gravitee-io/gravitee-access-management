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

import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DialectHelper {

    Single<List<Map<String, Object>>> buildAndProcessHistogram(DatabaseClient dbClient, ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria);

    SearchQuery buildHistogramQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria);

    SearchQuery buildGroupByQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria);

    SearchQuery buildSearchQuery(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria);

    String tableExists(String table, String schema);

    String buildPagingClause(int page, int size);

    String buildLimitClause(int limit);

    void setAuditsTable(String auditsTable);

    void setAuditAccessPointsTable(String auditAccessPointsTable);

    void setAuditOutcomesTable(String auditOutcomesTable);

    void setAuditEntitiesTable(String auditEntitiesTable);

    String checkForeignKeyExists(String tableName, String constraintName, String schema);

    String addForeignKey(String childTable, String parentTable, String fkColumnName, String constraintName);
}
