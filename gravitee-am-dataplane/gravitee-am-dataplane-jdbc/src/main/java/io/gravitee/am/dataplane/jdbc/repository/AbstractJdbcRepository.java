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
package io.gravitee.am.dataplane.jdbc.repository;

import com.github.dozermapper.core.DozerBeanMapperBuilder;
import com.github.dozermapper.core.Mapper;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.dataplane.jdbc.dialect.DatabaseDialectHelper;
import io.gravitee.am.dataplane.jdbc.mapper.LocalDateConverter;
import io.gravitee.am.repository.jdbc.exceptions.JdbcRepositoryExceptionMapper;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.CriteriaDefinition;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractJdbcRepository {
    public static final String USER_ID_FIELD = "user_id";
    public static final String USER_EXTERNAL_ID_FIELD = "user_external_id";
    public static final String USER_SOURCE_FIELD = "user_source";

    protected static final UserIdFields DEFAULT_USER_ID_FIELDS = new UserIdFields(USER_ID_FIELD, USER_SOURCE_FIELD, USER_EXTERNAL_ID_FIELD);

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

    private static final JdbcRepositoryExceptionMapper exceptionMapper = new JdbcRepositoryExceptionMapper();

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
                + ") VALUES (:" + String.join(",:", columns) + ")";
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

    /**
     * @deprecated Use {@link DateHelper#toLocalDateTime} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected LocalDateTime toLocalDateTime(Date date) {
        return DateHelper.toLocalDateTime(date);
    }

    /**
     * @deprecated Use {@link DateHelper#toDate} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected Date toDate(LocalDateTime localDateTime) {
        return DateHelper.toDate(localDateTime);
    }

    protected <T> Flowable<T> findAll(Query query, Class<T> type) {
        return fluxToFlowable(getTemplate().select(type)
                .matching(query).all());
    }

    protected <T> Maybe<T> findOne(Query query, Class<T> type) {
        return monoToMaybe(getTemplate().select(type)
                .matching(query).one());
    }


    protected final Criteria userIdMatches(UserId userId, UserIdFields userIdFields) {
        var idColumn = userIdFields.idField();
        var extIdColumn = userIdFields.externalIdField();
        var sourceColumn = userIdFields.sourceField();
        if (userId.id() != null && userId.hasExternal()) {
            var actualQuery = where(idColumn).is(userId.id()).or(where(extIdColumn).is(userId.externalId()).and(sourceColumn).is(userId.source()));
            // this somewhat silly workaround is required to force correct parentheses placement in the final query
            return Criteria.empty().and(actualQuery);
        } else if (userId.hasExternal()) {
            return where(extIdColumn).is(userId.externalId()).and(sourceColumn).is(userId.source());
        } else if (userId.id() != null){
            return where(idColumn).is(userId.id());
        } else {
            throw new IllegalArgumentException("attempt to search by an empty UserId");
        }
    }


    protected Criteria userIdMatches(UserId user) {
        return userIdMatches(user, DEFAULT_USER_ID_FIELDS);
    }

    protected CriteriaDefinition referenceMatches(Reference reference) {
        return where("reference_type").is(reference.type()).and("reference_id").is(reference.id());
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

    /**
     * Maps a throwable to a RepositoryConnectionException if it represents a connection error,
     * otherwise returns the original throwable.
     *
     * @param throwable the exception to map
     * @return a RepositoryConnectionException if it's a connection error, otherwise the original throwable
     */
    protected Throwable mapException(Throwable throwable) {
        return exceptionMapper.map(throwable);
    }

    /**
     * Maps a throwable to a RepositoryConnectionException in a Maybe error if it represents a connection error,
     * otherwise returns the original throwable as a Maybe.
     *
     * @param error the exception to map
     * @return a Maybe error if it's a connection error, otherwise the original throwable as a Maybe
     */
    protected <T> MaybeSource<T> mapExceptionAsMaybe(Throwable error) {
        return Maybe.error(mapException(error));
    }
    /**
     * Maps a throwable to a RepositoryConnectionException in a Flowable error if it represents a connection error,
     * otherwise returns the original throwable as a Flowable.
     *
     * @param error the exception to map
     * @return a Flowable error if it's a connection error, otherwise the original throwable as a Flowable
     */
    protected <T> Flowable<T> mapExceptionAsFlowable(Throwable error) {
        return Flowable.fromMaybe(mapExceptionAsMaybe(error));
    }

    /**
     * Maps a throwable to a RepositoryConnectionException in a Single error if it represents a connection error,
     * otherwise returns the original throwable as a Single.
     *
     * @param error the exception to map
     * @return a Single error if it's a connection error, otherwise the original throwable as a Single
     */
    protected <T> SingleSource<T> mapExceptionAsSingle(Throwable error) {
        return Single.fromMaybe(mapExceptionAsMaybe(error));
    }
}
