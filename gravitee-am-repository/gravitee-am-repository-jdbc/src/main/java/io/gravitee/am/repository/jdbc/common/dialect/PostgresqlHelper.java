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
import io.gravitee.am.repository.jdbc.common.JSONMapper;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PostgresqlHelper extends AbstractDialectHelper {

    public PostgresqlHelper(R2dbcDialect dialect, String collation) {
        super(dialect, collation);
    }

    protected void loadJdbcDriver() throws Exception {
        Class.forName("org.postgresql.Driver");
    }

    @Override
    public DatabaseClient.GenericInsertSpec<Map<String, Object>> addJsonField(DatabaseClient.GenericInsertSpec<Map<String, Object>> spec, String name, Object value) {
        try {
            return value == null ?
                    spec.nullValue(SqlIdentifier.quoted(name), Json.class) :
                    spec.value(SqlIdentifier.quoted(name), Json.of(JSONMapper.toJson(value)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<SqlIdentifier, Object> addJsonField(Map<SqlIdentifier, Object> spec, String name, Object value) {
        if (value == null) {
            spec.put(SqlIdentifier.quoted(name), (Json)null);
        } else {
            spec.put(SqlIdentifier.quoted(name), Json.of(JSONMapper.toJson(value)));
        }
        return spec;
    }

    protected ScimUserSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimUserSearch search) {
        String[] path = convertFieldName(criteria).split("\\.");
        final String operator = criteria.getOperator().toLowerCase().trim();
        final String value = criteria.getFilterValue();
        switch (operator) {
            case "eq":
                queryBuilder.append(path[0]+" ->> '"+path[1] + "' = '" + value + "' ");
                break;
            case "ne":
                queryBuilder.append(path[0]+" ->> '"+path[1] + "' != '" + value + "' ");
                break;
            case "pr":
                queryBuilder.append(path[0]+" ? '"+path[1] + "'");
                break;
            case "co":
                queryBuilder.append(path[0]+" ->> '"+path[1] + "' like '%" + value + "%' ");
                break;
            case "sw":
                queryBuilder.append(path[0]+" ->> '"+path[1] + "' like '" + value + "%' ");
                break;
            case "ew":
                queryBuilder.append(path[0]+" ->> '"+path[1] + "' like '%" + value + "' ");
                break;
            default:
                // TODO gt, ge, lt, le not managed yet... we have to know the json field type in order to adapt the clause
                throw new IllegalArgumentException("Operator '" + criteria + "' isn't authorized on JSON fields");
        }
        search.updateBuilder(queryBuilder);
        return search;
    }

    @Override
    protected StringBuilder buildSearchUser(boolean wildcard, StringBuilder builder) {
        return builder.append("u.reference_id = :refId")
                .append(" AND u.reference_type = :refType")
                .append(" AND (")
                .append(" u.username ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.email ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR  u.additional_information->>email ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.display_name ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.first_name ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.last_name ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" ) ");
    }

    @Override
    protected StringBuilder buildSearchApplications(boolean wildcard, StringBuilder builder) {
        return builder.append("a.domain = :domain")
                .append(" AND (")
                .append(" a.name ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR a.settings->'oauth'->>'clientId' ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" ) ");
    }

    protected String buildPagingClause(int page, int size) {
        return buildPagingClause("id", page, size);
    }

    protected String buildPagingClause(String field, int page, int size) {
        return " ORDER BY "+field+" LIMIT " + size + " OFFSET " + (page * size);
    }

    @Override
    public String buildFindUserByDomainAndEmail(ReferenceType referenceType, String referenceId, String email, boolean strict) {
        return new StringBuilder("SELECT * FROM users u WHERE ")
                .append(" u.reference_type = :refType")
                .append(" AND u.reference_id = :refId AND (")
                .append(strict ? "u.email" : "UPPER(u.email)")
                .append(strict ? " = :email OR " : " = UPPER(:email) OR ")
                .append(strict ? "u.additional_information->>email" : " UPPER(u.additional_information->>email)")
                .append(strict ? " = :email" : " = UPPER(:email) ")
                .append(")").toString();
    }

    @Override
    public String buildFindApplicationByDomainAndClient() {
        return new StringBuilder("SELECT * FROM applications a WHERE ")
                .append(" a.domain = :domain ")
                .append(" AND a.settings->'oauth'->>'clientId' = :clientId").toString();
    }
}
