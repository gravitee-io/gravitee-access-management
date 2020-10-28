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
public class MsSqlHelper extends AbstractDialectHelper {
    public MsSqlHelper(R2dbcDialect dialect) {
        super(dialect);
    }

    @Override
    protected void loadJdbcDriver() throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Override
    protected ScimUserSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimUserSearch search) {
        String[] path = convertFieldName(criteria).split("\\.");
        final String operator = criteria.getOperator().toLowerCase().trim();
        final String value = criteria.getFilterValue();
        switch (operator) {
            case "eq":
                queryBuilder.append(path[0]+"_"+path[1] +" = '" + value + "'");
                break;
            case "ne":
                queryBuilder.append(path[0]+"_"+path[1] +" <> '" + value + "'");
                break;
            case "pr":
                queryBuilder.append(path[0]+"_"+path[1] +" IS NOT NULL ");
                break;
            case "co":
                queryBuilder.append(path[0]+"_"+path[1] +" LIKE '%" + value+"%'");
                break;
            case "sw":
                queryBuilder.append(path[0]+"_"+path[1] +" LIKE '" + value + "%'");
                break;
            case "ew":
                queryBuilder.append(path[0]+"_"+path[1] +" LIKE '%" + value + "'");
                break;
            default:
                // TODO gt, ge, lt, le not managed yet... we have to know the json field type in order to adapt the clause
                throw new IllegalArgumentException("Operator '" + criteria + "' isn't authorized on JSON fields");
        }
        search.updateBuilder(queryBuilder);
        return search;
    }

    protected String buildPagingClause(int page, int size) {
        return " ORDER BY id OFFSET "+ (page * size) +" ROWS FETCH NEXT " + size + " ROWS ONLY ";
    }
}
