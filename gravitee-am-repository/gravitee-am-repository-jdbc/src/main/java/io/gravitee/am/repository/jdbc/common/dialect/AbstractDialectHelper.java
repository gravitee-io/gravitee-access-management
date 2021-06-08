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

    public static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final R2dbcDialect dialect;

    protected final String collation;

    public AbstractDialectHelper(R2dbcDialect dialect, String collation) {
        this.dialect = dialect;
        this.collation = collation;
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
            return Optional.ofNullable(LocalDateTime.parse(criteria.getFilterValue(), UTC_FORMATTER));
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

    protected abstract String buildPagingClause(String field, int page, int size);

    @Override
    public String buildSearchUserQuery(boolean wildcard, int page, int size, boolean organizationUser) {
        StringBuilder builder = new StringBuilder("SELECT * FROM " + (organizationUser ? "organization_users" : "users" )+ " u WHERE ");
        return buildSearchUser(wildcard, builder)
                .append(buildPagingClause("username", page, size))
                .toString();
    }

    @Override
    public String buildCountUserQuery(boolean wildcard, boolean organizationUser) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT u.id) FROM " + (organizationUser ? "organization_users" : "users" )+ " u WHERE ");
        return buildSearchUser(wildcard, builder)
                .toString();
    }

    @Override
    public String buildCountScopeQuery(boolean wildcardSearch){
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT s."+toSql(SqlIdentifier.quoted("key"))+") FROM scopes s WHERE ");
        return buildSearchScope(wildcardSearch, builder)
                .toString();
    }

    @Override
    public String buildSearchScopeQuery(boolean wildcardSearch, int page, int size){
        StringBuilder builder = new StringBuilder("SELECT * FROM scopes s WHERE ");
        return buildSearchScope(wildcardSearch, builder)
                .append(buildPagingClause("s."+toSql(SqlIdentifier.quoted("key")), page, size))
                .toString();
    }

    private StringBuilder buildSearchScope(boolean wildcardSearch, StringBuilder builder) {
        return builder.append("domain = :domain ")
                .append(" AND upper(s."+toSql(SqlIdentifier.quoted("key"))+") " + (wildcardSearch ? "LIKE" : "="))
                .append(" :value");
    }

    protected StringBuilder buildSearchUser(boolean wildcard, StringBuilder builder) {
        return builder.append("u.reference_id = :refId")
                .append(" AND u.reference_type = :refType")
                .append(" AND (")
                .append(" u.username ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.email ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR u.additional_information_email ").append(wildcard ? "LIKE " : "= ")
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
    public String buildFindUserByReferenceAndEmail(ReferenceType referenceType, String referenceId, String email, boolean strict) {
        boolean organizationUser = (ReferenceType.ORGANIZATION == referenceType);
        return new StringBuilder("SELECT * FROM " + (organizationUser ? "organization_users" : "users" )+ " u WHERE ")
                .append(" u.reference_type = :refType ")
                .append(" AND u.reference_id = :refId AND (")
                .append(strict ? "u.email" : "UPPER(u.email)")
                .append(strict ? " = :email COLLATE " + collation : " = UPPER(:email)")
                .append(" OR ")
                .append(strict ? "u.additional_information_email" : "UPPER(u.additional_information_email)")
                .append(strict ? " = :email COLLATE " + collation : " = UPPER(:email)")
                .append(")").toString();
    }

    @Override
    public String buildSearchApplicationsQuery(boolean wildcard, int page, int size) {
        StringBuilder builder = new StringBuilder("SELECT * FROM applications a WHERE ");
        return buildSearchApplications(wildcard, builder)
                .append(buildPagingClause(page, size))
                .toString();
    }

    @Override
    public String buildCountApplicationsQuery(boolean wildcard) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT a.id) FROM applications a WHERE ");
        return buildSearchApplications(wildcard, builder)
                .toString();
    }

    protected StringBuilder buildSearchApplications(boolean wildcard, StringBuilder builder) {
        return builder.append("a.domain = :domain")
                .append(" AND (")
                .append(" upper(a.name) ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" OR upper(a.settings_client_id) ").append(wildcard ? "LIKE " : "= ")
                .append(":value")
                .append(" ) ");
    }

    @Override
    public String buildFindApplicationByDomainAndClient() {
        return new StringBuilder("SELECT * FROM applications a WHERE ")
                .append(" a.domain = :domain ")
                .append(" AND a.settings_client_id = :clientId").toString();
    }

    @Override
    public String buildSearchRoleQuery(boolean wildcard, int page, int size) {
        StringBuilder builder = new StringBuilder("SELECT * FROM roles r WHERE ");
        return buildSearchRole(wildcard, builder)
                .append(buildPagingClause("name", page, size))
                .toString();
    }

    @Override
    public String buildCountRoleQuery(boolean wildcard) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT r.id) FROM roles r WHERE ");
        return buildSearchRole(wildcard, builder)
                .toString();
    }

    protected StringBuilder buildSearchRole(boolean wildcard, StringBuilder builder) {
        return builder.append("r.reference_id = :refId")
                .append(" AND r.reference_type = :refType")
                .append(" AND")
                .append(wildcard ? " UPPER(r.name)" : " r.name")
                .append(wildcard ? " LIKE " : " =")
                .append(wildcard ? " UPPER(:value) " : " :value ");
    }
}
