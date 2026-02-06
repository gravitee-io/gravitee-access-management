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

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MsSqlHelper extends AbstractDialectHelper {

    public static final String SQL_LIKE = " LIKE ";
    // used to escape keyword in SQLServer... in v1.1.0 of SpringData R2dbc,
    // the toSql() method present into AbstractDialectHelper was enough
    // but after the upgrade to 1.4.0, we have to do it in that way.
    private final IdentifierProcessing sqlServerIdentifierProcessing = IdentifierProcessing.create(new IdentifierProcessing.Quoting("[", "]"), IdentifierProcessing.LetterCasing.AS_IS);

    @Override
    public String toSql(SqlIdentifier sql) {
        return sql.toSql(sqlServerIdentifierProcessing);
    }

    public MsSqlHelper(R2dbcDialect dialect, String collation) {
        super(dialect, collation == null ? "Latin1_General_BIN2" : collation);
    }

    @Override
    protected void loadJdbcDriver() throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Override
    protected ScimSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimSearch search) {
        String[] path = convertFieldName(criteria).split("\\.");
        final String operator = criteria.getOperator().toLowerCase().trim();
        final String value = criteria.getFilterValue();
        final String bindValueName = (":" + path[0] + path[1].substring(0, 1).toUpperCase() + path[1].substring(1)).replaceAll("_", "");
        final String bindValueKey = bindValueName.substring(1);
        switch (operator) {
            case "eq":
                queryBuilder.append(path[0]+"_"+path[1] +" = " + bindValueName + "");
                search.addBinding(bindValueKey, value);
                break;
            case "ne":
                queryBuilder.append(path[0]+"_"+path[1] +" <> " + bindValueName + "");
                search.addBinding(bindValueKey, value);
                break;
            case "pr":
                queryBuilder.append(path[0]+"_"+path[1] +" IS NOT NULL ");
                break;
            case "co":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + "");
                search.addBinding(bindValueKey, "%" + value + "%");
                break;
            case "sw":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + "");
                search.addBinding(bindValueKey, value + "%");
                break;
            case "ew":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + "");
                search.addBinding(bindValueKey, "%" + value);
                break;
            default:
                // TODO gt, ge, lt, le not managed yet... we have to know the json field type in order to adapt the clause
                throw new IllegalArgumentException("Operator '" + criteria + "' isn't authorized on JSON fields");
        }
        search.updateBuilder(queryBuilder);
        return search;
    }

    public String buildPagingClauseUsingOffset(String field, boolean asc, int offset, int size) {
        String direction = asc ? "" : " DESC";
        return " ORDER BY " + field + direction +" OFFSET "+ offset +" ROWS FETCH NEXT " + size + " ROWS ONLY ";
    }

    @Override
    public boolean supportsReturningOnDelete() {
        return true;
    }

    @Override
    public String buildAuthorizationCodeDeleteAndReturnQuery() {
        return "DELETE FROM authorization_codes OUTPUT DELETED.* where code = :code and client_id = :clientId and (expire_at > :now or expire_at is null)";
    }

    /**
     * Escapes SQL Server LIKE pattern special characters using backslash escape.
     * In SQL Server, [ and ] are used for character class matching in LIKE patterns.
     * When using ESCAPE '\' clause, we escape these characters with backslash.
     * Also escapes _ which is a LIKE wildcard for a single character.
     *
     * @param value the value to escape (may already contain % from wildcard conversion)
     * @return the escaped value safe for use in SQL Server LIKE patterns with ESCAPE '\'
     */
    @Override
    public String escapeLikePatternValue(String value) {
        if (value == null) {
            return null;
        }
        // Escape backslash first, then special characters
        // Note: % characters are intentional wildcards added by the repository,
        // so we don't escape them here
        return value
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("_", "\\_");
    }

    /**
     * Returns the ESCAPE clause for LIKE patterns in SQL Server.
     * This is needed because SQL Server treats [ ] as special characters in LIKE.
     *
     * @return the ESCAPE clause to append to LIKE queries
     */
    @Override
    public String getLikeEscapeClause() {
        return " ESCAPE '\\' ";
    }

}
