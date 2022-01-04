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
package io.gravitee.am.repository.jdbc.common.dialect;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUser;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.Flowable;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DatabaseDialectHelper {

    String toSql(SqlIdentifier sql);

    Map<SqlIdentifier, Object> addJsonField(Map<SqlIdentifier, Object> insertSpec, String name, Object value);

    DatabaseClient.GenericExecuteSpec addJsonField(DatabaseClient.GenericExecuteSpec spec, String name, Object value);

    ScimUserSearch prepareScimSearchUserQuery(StringBuilder queryBuilder, FilterCriteria filterCriteria, int page, int size);

    String buildFindUserByReferenceAndEmail(ReferenceType referenceType, String referenceId, String email, boolean strict);

    String buildSearchUserQuery(boolean wildcard, int page, int size, boolean organizationUser);

    default String buildSearchUserQuery(boolean wildcard, int page, int size) {
        return buildSearchUserQuery(wildcard, page, size, false);
    }

    String buildCountUserQuery(boolean wildcard, boolean organizationUser);

    default String buildCountUserQuery(boolean wildcard) {
        return buildCountUserQuery(wildcard, false);
    }

    String buildSearchApplicationsQuery(boolean wildcard, int page, int size);

    String buildCountApplicationsQuery(boolean wildcard);

    String buildFindApplicationByDomainAndClient();

    String buildSearchScopeQuery(boolean wildcardSearch, int page, int size);

    String buildCountScopeQuery(boolean wildcardSearch);

    String buildSearchRoleQuery(boolean wildcard, int page, int size);

    String buildCountRoleQuery(boolean wildcard);

    String buildPagingClause(int page, int size);

    String buildPagingClause(String field, int page, int size);
}
