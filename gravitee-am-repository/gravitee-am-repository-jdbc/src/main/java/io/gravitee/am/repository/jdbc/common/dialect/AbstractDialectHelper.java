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
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractDialectHelper implements DatabaseDialectHelper {

    public static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String ACTIVE = "active";
    public static final String META_CREATED = "meta.created";
    public static final String META_LAST_MODIFIED = "meta.lastModified";
    public static final String EMAILS_VALUE = "emails.value";
    public static final String LOCALE = "locale";
    public static final String ORGANIZATION_USERS = "organization_users";
    public static final String USERS = "users";
    public static final String U_WHERE = " u WHERE ";
    public static final String VALUE = ":value";
    public static final String LIKE = "LIKE ";
    public static final String ID = "id";
    public static final String DISPLAY_NAME = "displayName";
    public static final String USER_NAME = "userName";
    public static final String NAME_FAMILY_NAME = "name.familyName";
    public static final String NAME_GIVEN_NAME = "name.givenName";
    public static final String NAME_MIDDLE_NAME = "name.middleName";
    public static final String PROFILE_URL = "profileUrl";
    public static final String TIMEZONE = "timezone";
    public static final String ADDITIONAL_INFORMATION_DOT = "additional_information.";
    public static final String ADDITIONAL_INFORMATION_CAMEL_CASE_DOT = "additionalInformation.";

    private final R2dbcDialect dialect;

    protected final String collation;

    protected AbstractDialectHelper(R2dbcDialect dialect, String collation) {
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
    public DatabaseClient.GenericExecuteSpec addJsonField(DatabaseClient.GenericExecuteSpec spec, String name, Object value) {
        try {
            return value == null ?
                    spec.bindNull(name, String.class) :
                    spec.bind(name, JSONMapper.toJson(value));
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
    public ScimSearch prepareScimSearchQueryUsingOffset(StringBuilder queryBuilder, FilterCriteria criteria, String sortField, int offset, int size, ScimRepository scimRepository) {
        ScimSearch search = new ScimSearch();
        processFilters(queryBuilder, criteria, search, scimRepository);
        search.buildQueries(size > 0 ? buildPagingClauseUsingOffset(StringUtils.hasLength(sortField)? sortField : "id", true, offset, size): "");
        return search;
    }

    @Override
    public ScimSearch prepareScimSearchQuery(StringBuilder queryBuilder, FilterCriteria filterCriteria, String sortField, int page, int size, ScimRepository scimRepository) {
        return prepareScimSearchQueryUsingOffset(queryBuilder, filterCriteria, sortField, page * size, size, scimRepository);
    }

    private ScimSearch processFilters(StringBuilder queryBuilder, FilterCriteria criteria, ScimSearch search, ScimRepository scimRepository) {
        if (criteria.getFilterComponents() != null && !criteria.getFilterComponents().isEmpty()) {
            queryBuilder.append("( ");
            Iterator<FilterCriteria> iterator = criteria.getFilterComponents().iterator();
            while(iterator.hasNext()) {
                FilterCriteria operand = iterator.next();
                search = processFilters(queryBuilder, operand, search, scimRepository);
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
                String filterName = ScimRepository.GROUPS.equals(scimRepository) ? convertGroupsFieldName(criteria) : convertFieldName(criteria);
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
        if (filterName == null) {
            return false;
        }

        return switch (filterName) {
            case ID, USER_NAME, ACTIVE, META_CREATED, META_LAST_MODIFIED, EMAILS_VALUE -> false;
            case NAME_FAMILY_NAME, NAME_GIVEN_NAME, NAME_MIDDLE_NAME, PROFILE_URL, LOCALE, TIMEZONE -> true;
            default -> filterName.startsWith(ADDITIONAL_INFORMATION_DOT) || filterName.startsWith(ADDITIONAL_INFORMATION_CAMEL_CASE_DOT);
        };
    }

    protected final String convertFieldName(FilterCriteria criteria) {
        String filterName = criteria.getFilterName();
        if (filterName == null) {
            return null;
        }

        switch (filterName) {
            case ID:
                return ID;
            case USER_NAME:
                return "username";
            case NAME_FAMILY_NAME:
                return "additional_information.family_name";
            case NAME_GIVEN_NAME:
                return "additional_information.given_name";
            case NAME_MIDDLE_NAME:
                return "additional_information.middle_name";
            case META_CREATED:
                return "created_at";
            case META_LAST_MODIFIED:
                return "updated_at";
            case PROFILE_URL:
                return "additional_information.profile";
            case LOCALE:
                return "additional_information.locale";
            case TIMEZONE:
                return "additional_information.zoneinfo";
            case ACTIVE:
                return "enabled";
            case EMAILS_VALUE:
                return "email";
            case DISPLAY_NAME:
                return "display_name";
            case "meta.loggedAt":
                return "logged_at";
            case "meta.lastLoginWithCredentials":
                return "last_login_with_credentials";
            case "meta.lastPasswordReset":
                return "last_password_reset";
            case "meta.mfaEnrollmentSkippedAt":
                return "mfa_enrollment_skipped_at";
            case "meta.accountLockedAt":
                return "account_locked_at";
            case "meta.accountLockedUntil":
                return "account_locked_until";
            default:
                if (filterName.startsWith(ADDITIONAL_INFORMATION_CAMEL_CASE_DOT)) {
                    return filterName.replaceFirst("additionalInformation", "additional_information");
                } else {
                    return filterName;
                }
        }
    }

    private String convertGroupsFieldName(FilterCriteria criteria) {
        String filterName = criteria.getFilterName();
        if (filterName == null) {
            return null;
        }

        return switch (filterName) {
            case ID -> ID;
            case META_CREATED -> "created_at";
            case META_LAST_MODIFIED -> "updated_at";
            case DISPLAY_NAME -> "name";
            default -> filterName;
        };
    }

    protected final String operatorConverter(FilterCriteria criteria) {
        if (criteria.getOperator() != null) {
            final String operator = criteria.getOperator().toLowerCase().trim();
            return switch (operator) {
                case "and" -> " AND ";
                case "or" -> " OR ";
                case "eq" -> " = ";
                case "ne" -> " <> ";
                case "gt" -> " > ";
                case "ge" -> " >= ";
                case "lt" -> " < ";
                case "le" -> " <= ";
                case "pr" -> " IS NOT NULL ";
                case "co", "sw", "ew" -> " LIKE ";
                default -> " ";
            };
        }
        return " ";
    }

    protected final Optional<Object> fieldValue(FilterCriteria criteria) {
        if (isDateInput(criteria.getFilterName())) {
            return Optional.ofNullable(criteria.getFilterValue())
                    .map(value -> LocalDateTime.parse(value, UTC_FORMATTER));
        }

        if (isBooleanInput(criteria.getFilterName())) {
            return Optional.ofNullable(criteria.getFilterValue())
                    .map(value -> Boolean.valueOf(criteria.getFilterValue()));
        }

        String result = criteria.getFilterValue();
        final String operator = criteria.getOperator().toLowerCase().trim();
        return switch (operator) {
            case "co" ->  Optional.of("%" + result + "%");
            case "sw" ->  Optional.of(result + "%");
            case "ew" ->  Optional.of("%" + result);
            case "pr" ->
                // IS NOT NULL operator doesn't need value
                    Optional.empty();
            default -> Optional.of(result);
        };
    }


    private boolean isDateInput(String filterName) {
        return META_CREATED.equals(filterName) ||
                META_LAST_MODIFIED.equals(filterName) ||
                "meta.loggedAt".equals(filterName) ||
                "meta.lastLoginWithCredentials".equals(filterName) ||
                "meta.lastPasswordReset".equals(filterName) ||
                "meta.mfaEnrollmentSkippedAt".equals(filterName) ||
                "meta.accountLockedAt".equals(filterName) ||
                "meta.accountLockedUntil".equals(filterName);
    }

    private boolean isBooleanInput(String filterName) {
        return ACTIVE.equals(filterName);
    }

    protected abstract ScimSearch processJsonFilter(StringBuilder queryBuilder, FilterCriteria criteria, ScimSearch search);

    @Override
    public String buildSearchUserQuery(boolean wildcard, int page, int size, boolean organizationUser) {
        StringBuilder builder = new StringBuilder("SELECT * FROM " + (organizationUser ? ORGANIZATION_USERS : USERS) + U_WHERE);
        return buildSearchUser(wildcard, builder)
                .append(buildPagingClause("username", true, page, size))
                .toString();
    }

    @Override
    public String buildCountUserQuery(boolean wildcard, boolean organizationUser) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT u.id) FROM " + (organizationUser ? ORGANIZATION_USERS : USERS) + U_WHERE);
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
                .append(buildPagingClause("s."+toSql(SqlIdentifier.quoted("key")), true, page, size))
                .toString();
    }

    private StringBuilder buildSearchScope(boolean wildcardSearch, StringBuilder builder) {
        return builder.append("domain = :domain ")
                .append(" AND upper(s.")
                .append(toSql(SqlIdentifier.quoted("key")))
                .append(") ")
                .append(wildcardSearch ? "LIKE" : "=")
                .append(" :value");
    }

    protected StringBuilder buildSearchUser(boolean wildcard, StringBuilder builder) {
        return builder.append("u.reference_id = :refId")
                .append(" AND u.reference_type = :refType")
                .append(" AND (")
                .append(" u.username ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" OR u.email ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" OR u.additional_information_email ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" OR u.display_name ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" OR u.first_name ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" OR u.last_name ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(" ) ");
    }

    @Override
    public String buildFindUserByReferenceAndEmail(ReferenceType referenceType, String referenceId, String email, boolean strict) {
        boolean organizationUser = (ReferenceType.ORGANIZATION == referenceType);
        return new StringBuilder("SELECT * FROM " + (organizationUser ? ORGANIZATION_USERS : USERS)+ U_WHERE)
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
    public String buildSearchProtectedResourceQuery(boolean wildcard, int page, int size, String sort, boolean asc) {
        StringBuilder builder = new StringBuilder("SELECT * FROM protected_resources pr WHERE ");
        return buildSearchProtectedResource(wildcard, builder)
                .append(buildPagingClause(sort, asc, page, size))
                .toString();
    }

    @Override
    public String buildCountProtectedResourceQuery(boolean wildcard) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT pr.id) FROM protected_resources pr WHERE ");
        return buildSearchProtectedResource(wildcard, builder)
                .toString();
    }

    protected StringBuilder buildSearchProtectedResource(boolean wildcard, StringBuilder builder) {
        String escapeSuffix = wildcard ? getLikeEscapeClause() : "";
        return builder.append("pr.domain_id = :domain_id")
                .append(" AND (")
                .append(" upper(pr.name) ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(escapeSuffix)
                .append(" OR upper(pr.client_id) ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(escapeSuffix)
                .append(" ) ");
    }

    @Override
    public String buildSearchApplicationsQuery(boolean wildcard, boolean withIds, int page, int size, String sort, boolean asc) {
        StringBuilder builder = new StringBuilder("SELECT * FROM applications a WHERE ");
        return buildSearchApplications(wildcard, withIds, builder)
                .append(buildPagingClause(sort, asc, page, size))
                .toString();
    }


    @Override
    public String buildCountApplicationsQuery(boolean wildcard, boolean withIds) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT a.id) FROM applications a WHERE ");
        return buildSearchApplications(wildcard, withIds, builder)
                .toString();
    }

    protected StringBuilder buildSearchApplications(boolean wildcard, boolean withIds, StringBuilder builder) {
        String escapeSuffix = wildcard ? getLikeEscapeClause() : "";
        return builder.append("a.domain = :domain")
                .append(withIds ? " AND a.id IN (:applicationIds)" : "")
                .append(" AND (")
                .append(" upper(a.name) ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(escapeSuffix)
                .append(" OR upper(a.settings_client_id) ").append(wildcard ? LIKE : "= ")
                .append(VALUE)
                .append(escapeSuffix)
                .append(" ) ");
    }

    @Override
    public String buildSearchApplicationsQueryWithTypes(boolean wildcard, boolean withIds, int page, int size, String sort, boolean asc, List<String> types) {
        StringBuilder builder = new StringBuilder("SELECT * FROM applications a WHERE ");
        return buildSearchApplications(wildcard, withIds, builder, types)
                .append(buildPagingClause(sort, asc, page, size))
                .toString();
    }

    @Override
    public String buildCountApplicationsQueryWithTypes(boolean wildcard, boolean withIds, List<String> types) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT a.id) FROM applications a WHERE ");
        return buildSearchApplications(wildcard, withIds, builder, types)
                .toString();
    }

    protected StringBuilder buildSearchApplications(boolean wildcard, boolean withIds, StringBuilder builder, List<String> types) {
        StringBuilder result = buildSearchApplications(wildcard, withIds, builder);
        if (types != null && !types.isEmpty()) {
            result.append(" AND a.type IN (:types)");
        }
        return result;
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
                .append(buildPagingClause("name", true, page, size))
                .toString();
    }

    @Override
    public String buildCountRoleQuery(boolean wildcard) {
        StringBuilder builder = new StringBuilder("SELECT COUNT(DISTINCT r.id) FROM roles r WHERE ");
        return buildSearchRole(wildcard, builder)
                .toString();
    }

    @Override
    public String recursiveTokenDeleteQuery(String whereClause) {
        return """
                WITH RECURSIVE token_tree AS (
                SELECT token FROM tokens WHERE %s
                UNION ALL
                SELECT t.token FROM tokens t
                JOIN token_tree tt ON (t.parent_subject_jti = tt.token OR t.parent_actor_jti = tt.token)
                ) DELETE FROM tokens WHERE token IN (SELECT token FROM token_tree)
                """.formatted(whereClause);
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
