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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcCursorRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.gravitee.am.repository.management.api.ApplicationCursorRepository;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.repository.jdbc.management.api.JdbcApplicationRepository.COL_DOMAIN;
import static io.gravitee.am.repository.jdbc.management.api.JdbcApplicationRepository.COL_ID;
import static io.gravitee.am.repository.jdbc.management.api.JdbcApplicationRepository.COL_NAME;
import static io.gravitee.am.repository.jdbc.management.api.JdbcApplicationRepository.COL_UPDATED_AT;

@Component
public class JdbcApplicationCursorRepository
        extends AbstractJdbcCursorRepository<JdbcApplication, Application, ApplicationCursorRequest>
        implements ApplicationCursorRepository {

    private static final String SORT_FIELD_NAME = "name";
    private static final String SORT_FIELD_UPDATED_AT = "updatedAt";

    @Autowired
    private JdbcApplicationRepository applicationRepository;

    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainCursor(String domain, ApplicationCursorRequest cursor, int limit) {
        return findCursorPage(buildQuery(domain, null, cursor), cursor, limit);
    }

    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainAndIdsCursor(String domain, List<String> applicationIds, ApplicationCursorRequest cursor, int limit) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Single.just(new CursorPage<>(List.of(), null, 0L));
        }
        return findCursorPage(buildQuery(domain, applicationIds, cursor), cursor, limit);
    }

    private CursorQuerySpec buildQuery(String domain, List<String> applicationIds, ApplicationCursorRequest cursor) {
        StringBuilder where = new StringBuilder(applicationColumn(COL_DOMAIN)).append(" = :domain");

        if (applicationIds != null) {
            where.append(" AND ").append(applicationColumn(COL_ID)).append(" IN (:applicationIds)");
        }

        String queryValue = null;
        if (StringUtils.hasText(cursor.getQuery())) {
            boolean wildcardMatch = cursor.getQuery().contains("*");
            String preparedQuery = wildcardMatch ? cursor.getQuery().replaceAll("\\*+", "%") : cursor.getQuery();
            queryValue = databaseDialectHelper.escapeLikePatternValue(preparedQuery).toUpperCase();
            String operator = wildcardMatch ? " LIKE :value" + databaseDialectHelper.getLikeEscapeClause() : " = :value";
            where.append(" AND (")
                    .append("UPPER(").append(applicationColumn(COL_NAME)).append(")").append(operator)
                    .append(" OR ")
                    .append("UPPER(").append(databaseDialectHelper.applicationClientIdColumn("a")).append(")").append(operator)
                    .append(")");
        }

        String filter = where.toString();
        String countSql = "SELECT COUNT(DISTINCT " + applicationColumn(COL_ID) + ") FROM applications a WHERE " + filter;
        String selectSql = "SELECT * FROM applications a WHERE " + filter;
        String finalQueryValue = queryValue;

        return new CursorQuerySpec(
                selectSql,
                countSql,
                spec -> {
                    spec = spec.bind(COL_DOMAIN, domain);
                    if (applicationIds != null) {
                        spec = spec.bind("applicationIds", applicationIds);
                    }
                    if (finalQueryValue != null) {
                        spec = spec.bind("value", finalQueryValue);
                    }
                    return spec;
                });
    }

    @Override
    protected Application toEntity(JdbcApplication row) {
        return applicationRepository.toEntity(row);
    }

    @Override
    protected Single<Application> completeEntity(Application entity) {
        return applicationRepository.completeApplication(entity);
    }

    @Override
    protected Class<JdbcApplication> rowType() {
        return JdbcApplication.class;
    }

    @Override
    protected String idColumn() {
        return applicationColumn(COL_ID);
    }

    @Override
    protected String sortColumn(ApplicationCursorRequest cursor) {
        return switch (sortField(cursor)) {
            case SORT_FIELD_NAME -> applicationColumn(COL_NAME);
            case SORT_FIELD_UPDATED_AT -> applicationColumn(COL_UPDATED_AT);
            default -> throw new IllegalArgumentException("Invalid sort field: " + cursor.getSortField());
        };
    }

    @Override
    protected Object sortFieldValue(ApplicationCursorRequest cursor) {
        return switch (sortField(cursor)) {
            case SORT_FIELD_NAME -> cursor.getLastSortValue();
            case SORT_FIELD_UPDATED_AT -> dateFromString(cursor.getLastSortValue());
            default -> throw new IllegalArgumentException("Invalid sort field: " + cursor.getSortField());
        };
    }

    @Override
    protected ApplicationCursorRequest nextCursor(ApplicationCursorRequest currentCursor, Application element) {
        String lastSortValue = switch (sortField(currentCursor)) {
            case SORT_FIELD_NAME -> element.getName();
            case SORT_FIELD_UPDATED_AT -> dateToString(element.getUpdatedAt());
            default -> throw new IllegalStateException();
        };
        return new ApplicationCursorRequest(
                lastSortValue,
                element.getId(),
                currentCursor.getSortDirection(),
                currentCursor.getSortField(),
                currentCursor.getPage() + 1,
                currentCursor.getQuery(),
                currentCursor.getEnabled(),
                currentCursor.getTypes());
    }

    private static String sortField(ApplicationCursorRequest cursor) {
        return cursor.getSortField() != null ? cursor.getSortField() : SORT_FIELD_UPDATED_AT;
    }

    private String applicationColumn(String column) {
        return "a." + databaseDialectHelper.toSql(SqlIdentifier.quoted(column));
    }

    private static String dateToString(Date date) {
        return date != null ? String.valueOf(date.getTime()) : "0";
    }

    private static LocalDateTime dateFromString(String value) {
        try {
            return DateHelper.toLocalDateTime(new Date(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cursor: sort value '" + value + "' is not a valid timestamp", e);
        }
    }
}
