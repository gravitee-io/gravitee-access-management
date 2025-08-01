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

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MySqlHelper extends AbstractDialectHelper {

    public static final String SQL_LIKE = " LIKE ";
    public static final String EMPTY_STRING = "";

    public MySqlHelper(R2dbcDialect dialect, String collation) {
        super(dialect, collation == null ? "utf8mb4_bin" : collation);
    }

    protected void loadJdbcDriver() throws Exception {
        Class.forName("com.mysql.jdbc.Driver") ;
    }

    @Override
    protected ScimSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimSearch search) {
        String[] path = convertFieldName(criteria).split("\\.");
        final String operator = criteria.getOperator().toLowerCase().trim();
        final String value = criteria.getFilterValue();
        final String bindValueName = (":" + path[0] + path[1].substring(0, 1).toUpperCase() + path[1].substring(1)).replaceAll("_", EMPTY_STRING);
        final String bindValueKey = bindValueName.substring(1);
        switch (operator) {
            case "eq":
                queryBuilder.append(path[0]+"_"+path[1] +" = " + bindValueName + EMPTY_STRING);
                search.addBinding(bindValueKey, value);
                break;
            case "ne":
                queryBuilder.append(path[0]+"_"+path[1] +" <> " + bindValueName + EMPTY_STRING);
                search.addBinding(bindValueKey, value);
                break;
            case "pr":
                queryBuilder.append(path[0]+"_"+path[1] +" IS NOT NULL ");
                break;
            case "co":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + EMPTY_STRING);
                search.addBinding(bindValueKey, "%" + value + "%");
                break;
            case "sw":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + EMPTY_STRING);
                search.addBinding(bindValueKey, value + "%");
                break;
            case "ew":
                queryBuilder.append(path[0]+"_"+path[1] + SQL_LIKE + bindValueName + EMPTY_STRING);
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
        return " ORDER BY " + field + direction + " LIMIT " + size + " OFFSET " + offset;
    }

    @Override
    public boolean supportsReturningOnDelete() {
        return false;
    }

    @Override
    public String buildAuthorizationCodeDeleteAndReturnQuery() {
        throw new UnsupportedOperationException("MySQL doesn't support returning deleted");
    }

}
