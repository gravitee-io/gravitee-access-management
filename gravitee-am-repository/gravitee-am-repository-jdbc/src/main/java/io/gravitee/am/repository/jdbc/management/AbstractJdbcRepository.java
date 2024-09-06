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
package io.gravitee.am.repository.jdbc.management;

import com.github.dozermapper.core.DozerBeanMapperBuilder;
import com.github.dozermapper.core.Mapper;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.LocalDateConverter;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractJdbcRepository {
    public static final String USER_ID_FIELD = "user_id";
    public static final String USER_EXTERNAL_ID_FIELD = "user_external_id";
    public static final String USER_SOURCE_FIELD = "user_source";
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
    @Autowired
    @Getter
    private R2dbcEntityTemplate template;

    @Autowired
    protected DatabaseDialectHelper databaseDialectHelper;

    @Autowired
    protected ReactiveTransactionManager tm;

    protected LocalDateConverter dateConverter = new LocalDateConverter();

    protected static final Mapper mapper = DozerBeanMapperBuilder.create().withMappingFiles(List.of("dozer.xml")).build();

    @Autowired
    protected MappingR2dbcConverter rowMapper;

    protected <T> DatabaseClient.GenericExecuteSpec addQuotedField(DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    protected Map<SqlIdentifier, Object> addQuotedField(Map<SqlIdentifier, Object> spec, String name, Object value) {
        spec.put(SqlIdentifier.unquoted(databaseDialectHelper.toSql(SqlIdentifier.quoted(name))), value);
        return spec;
    }

    protected String createInsertStatement(String table, List<String> columns) {
        return "INSERT INTO " + table + " (" +
                columns.stream().map(SqlIdentifier::quoted).map(databaseDialectHelper::toSql).collect(Collectors.joining(","))
                + ") VALUES (:" + columns.stream().collect(Collectors.joining(",:")) + ")";
    }

    protected String createUpdateStatement(String table, List<String> columns, List<String> whereClauseColumns) {
        StringBuilder builder = new StringBuilder("UPDATE " + table + " SET ");
        for (int i = 0; i < columns.size(); ++i) {
            final String colName = columns.get(i);
            builder.append(databaseDialectHelper.toSql(SqlIdentifier.quoted(colName)));
            builder.append(" = :");
            builder.append(colName);
            if (i + 1 != columns.size()) {
                builder.append(", ");
            }
        }
        builder.append(" WHERE ");
        for (int i = 0; i < whereClauseColumns.size(); ++i) {
            final String colName = whereClauseColumns.get(i);
            builder.append(databaseDialectHelper.toSql(SqlIdentifier.quoted(colName)));
            builder.append(" = :");
            builder.append(colName);

            if (i + 1 != whereClauseColumns.size()) {
                builder.append(" AND ");
            }
        }
        return builder.toString();
    }

    protected LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    protected Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }


    protected Criteria userMatches(UserId userId, String userIdField, String userExternalIdField, String userSourceField) {
        if (userId.hasExternal()) {
            return where(userIdField).is(userId.id()).or(where(userExternalIdField).is(userId.externalId()).and(userSourceField).is(userId.source()));
        } else {
            return where(userIdField).is(userId.id());
        }
    }

    protected Criteria userMatches(UserId user) {
        return userMatches(user, USER_ID_FIELD, USER_EXTERNAL_ID_FIELD, USER_SOURCE_FIELD);
    }

    /**
     * Holds information about what column to populate & how.
     *
     * @param <S> source entity type
     * @param <T> column type
     * @see #addQuotedFields
     */
    public record FieldSpec<S, T>(String columnName, Function<S, T> valueGetter, Class<T> valueType) {
    }

    protected <S> DatabaseClient.GenericExecuteSpec addQuotedFields(DatabaseClient.GenericExecuteSpec sql, List<FieldSpec<S, ?>> fields, S source) {
        return fields.stream()
                .reduce(sql,
                        (spec, field) -> addQuotedField(spec, field.columnName(), field.valueGetter().apply(source), field.valueType()),
                        (a, b) -> {
                            throw new UnsupportedOperationException("don't make this stream parallel");
                        });
    }
}
