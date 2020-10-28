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

import io.gravitee.am.repository.jdbc.common.JSONMapper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractDialectHelper implements DatabaseDialectHelper {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final R2dbcDialect dialect;

    public AbstractDialectHelper(R2dbcDialect dialect) {
        this.dialect = dialect;
        try {
            loadJdbcDriver();
        } catch (Exception e) {
            throw new RepositoryInitializationException("Unable to load JDBC driver", e);
        }
    }

    /**
     * Load JDBC driver in order to allow liquibase initialization
     * @throws Exception
     */
    protected abstract void loadJdbcDriver() throws Exception;

    public String toSql(SqlIdentifier sql) {
        return sql.toSql(dialect.getIdentifierProcessing());
    }

    @Override
    public DatabaseClient.GenericInsertSpec<Map<String, Object>> addJsonField(DatabaseClient.GenericInsertSpec<Map<String, Object>> spec, String name, Object value) {
        try {
            return value == null ?
                    spec.nullValue(SqlIdentifier.quoted(name), String.class) :
                    spec.value(SqlIdentifier.quoted(name), JSONMapper.toJson(value));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<SqlIdentifier, Object> addJsonField(Map<SqlIdentifier, Object> spec, String name, Object value) {
        spec.put(SqlIdentifier.quoted(name), value == null ? null : JSONMapper.toJson(value));
        return spec;
    }

    @Override
    public ScimUserSearch prepareScimSearchUserQuery(StringBuilder queryBuilder, FilterCriteria criteria, int page, int size) {
        ScimUserSearch search = new ScimUserSearch();
        processFilters(queryBuilder, criteria, search);
        search.buildQueries(buildPagingClause(page, size));
        return search;
    }

    private ScimUserSearch processFilters(StringBuilder queryBuilder, FilterCriteria criteria, ScimUserSearch search) {
        if (criteria.getFilterComponents() != null && !criteria.getFilterComponents().isEmpty()) {
            queryBuilder.append("( ");
            Iterator<FilterCriteria> iterator = criteria.getFilterComponents().iterator();
            while(iterator.hasNext()) {
                FilterCriteria operand = iterator.next();
                search = processFilters(queryBuilder, operand, search);
                queryBuilder = search.getQueryBuilder();
                if (iterator.hasNext()) {
                    queryBuilder.append(operatorConverter(criteria));
                }
            }
            queryBuilder.append(" )");
        } else {
            if (isJsonField(criteria)) {
                search = processJsonFilter(queryBuilder, criteria, search);
            } else {
                String filterName = convertFieldName(criteria);
                Optional fieldValue = fieldValue(criteria);
                if (fieldValue.isPresent()) {
                    String bindingName = search.addBinding(filterName, fieldValue.get());
                    queryBuilder.append(filterName).append(operatorConverter(criteria)).append(bindingName);
                } else {
                    queryBuilder.append(filterName).append(operatorConverter(criteria));
                }
            }
        }
        search.updateBuilder(queryBuilder);
        return search;
    }

    private boolean isJsonField(FilterCriteria criteria) {
        String filterName = criteria.getFilterName();
        if (filterName != null) {
            switch (filterName) {
                case "id":
                case "userName":
                case "active":
                case "meta.created":
                case "meta.lastModified":
                case "emails.value":
                    return false;
                case "name.familyName":
                case "name.givenName":
                case "name.middleName":
                case "profileUrl":
                case "locale":
                case "timezone":
                    return true;
            }
        }
        return false;
    }

    protected final String convertFieldName(FilterCriteria criteria) {
        String filterName = criteria.getFilterName();
        if (filterName == null) {
            return null;
        }

        switch (filterName) {
            case "id":
                return "id";
            case "userName":
                return "username";
            case "name.familyName":
                return "additional_information.family_name";
            case "name.givenName":
                return "additional_information.given_name";
            case "name.middleName":
                return "additional_information.middle_name";
            case "meta.created":
                return "created_at";
            case "meta.lastModified":
                return "updated_at";
            case "profileUrl":
                return "additional_information.profile";
            case "locale":
                return "additional_information.locale";
            case "timezone":
                return "additional_information.zoneinfo";
            case "active":
                return "enabled";
            case "emails.value":
                return "email";
            default:
                return filterName;
        }
    }

    protected final String operatorConverter(FilterCriteria criteria) {
        String result = " ";
        if ( criteria.getOperator() != null) {
            final String operator = criteria.getOperator().toLowerCase().trim();
            switch (operator) {
                case "and":
                    result = " AND ";
                    break;
                case "or":
                    result = " OR ";
                    break;
                case "eq":
                    result = " = ";
                    break;
                case "ne":
                    result = " <> ";
                    break;
                case "gt":
                    result = " > ";
                    break;
                case "ge":
                    result = " >= ";
                    break;
                case "lt":
                    result = " < ";
                    break;
                case "le":
                    result = " <= ";
                    break;
                case "pr":
                    result = " IS NOT NULL ";
                    break;
                case "co":
                case "sw":
                case "ew":
                    result = " LIKE ";
                    break;
            }
        }
        return result;
    }

    protected final Optional<Object> fieldValue(FilterCriteria criteria) {
        if (isDateInput(criteria.getFilterName())) {
            return Optional.ofNullable(LocalDateTime.parse(criteria.getFilterValue(), FORMATTER));
        }

        if (isBooleanInput(criteria.getFilterName())) {
            return Optional.ofNullable(Boolean.valueOf(criteria.getFilterValue()));
        }

        String result = criteria.getFilterValue();
        final String operator = criteria.getOperator().toLowerCase().trim();
        switch (operator) {
            case "co":
                result = "%"+result+"%";
                break;
            case "sw":
                result = result+"%";
                break;
            case "ew":
                result = "%"+result;
                break;
            case "pr":
                // IS NOT NULL operator doesn't need value
                return Optional.empty();
        }
        return Optional.of(result);
    }


    private boolean isDateInput(String filterName) {
        return "meta.created".equals(filterName) ||
                "meta.lastModified".equals(filterName);
    }

    private boolean isBooleanInput(String filterName) {
        return "active".equals(filterName);
    }

    protected abstract ScimUserSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimUserSearch search);

    protected abstract String buildPagingClause(int page, int size);

}
