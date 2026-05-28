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

import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.List;
import java.util.function.Function;

import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

public abstract class AbstractJdbcCursorRepository<T, R, C extends CursorRequest> extends AbstractJdbcRepository {

    public record CursorQuerySpec(
            String selectSql,
            String countSql,
            Function<DatabaseClient.GenericExecuteSpec, DatabaseClient.GenericExecuteSpec> binder) {
    }

    protected Single<CursorPage<R, C>> findCursorPage(
            CursorQuerySpec querySpec,
            C cursor,
            int limit) {

        String sortColumn = sortColumn(cursor);
        String idCol = idColumn();

        Single<Long> countOperation = monoToSingle(querySpec.binder()
                        .apply(getTemplate().getDatabaseClient().sql(querySpec.countSql()))
                        .map((row, rowMetadata) -> row.get(0, Long.class))
                        .first())
                .map(total -> total == null ? 0L : total);

        String orderByClause = buildOrderByClause(sortColumn, idCol, cursor);
        int offset = cursor.isFirstPage() ? cursor.getPage() * limit : 0;
        String selectSql = querySpec.selectSql()
                + buildCursorClause(cursor, sortColumn, idCol)
                + databaseDialectHelper.buildPagingClauseUsingOffset(orderByClause, offset, limit + 1);

        DatabaseClient.GenericExecuteSpec selectSpec = querySpec.binder().apply(getTemplate().getDatabaseClient().sql(selectSql));
        if (!cursor.isFirstPage()) {
            selectSpec = selectSpec.bind("cursorLastSortValue", sortFieldValue(cursor));
            selectSpec = selectSpec.bind("cursorLastId", cursor.getLastId());
        }

        Single<List<R>> dataOperation = fluxToFlowable(selectSpec
                        .map((row, rowMetadata) -> rowMapper.read(rowType(), row))
                        .all())
                .map(this::toEntity)
                .concatMap(entity -> completeEntity(entity).toFlowable())
                .toList();

        return Single.zip(countOperation, dataOperation, (totalCount, items) -> {
                    boolean hasNext = items.size() > limit;
                    List<R> data = hasNext ? items.subList(0, limit) : items;
                    if (hasNext && !data.isEmpty()) {
                        R last = data.get(data.size() - 1);
                        C next = nextCursor(cursor, last);
                        return new CursorPage<R, C>(data, next, totalCount);
                    }
                    return new CursorPage<R, C>(data, null, totalCount);
                })
                .observeOn(Schedulers.computation());
    }

    private String buildOrderByClause(String sortColumn, String idColumn, CursorRequest cursor) {
        String direction = cursor.getSortDirection().isAscending() ? " ASC" : " DESC";
        return sortColumn + direction + ", " + idColumn + direction;
    }

    private String buildCursorClause(CursorRequest cursor, String sortColumn, String idColumn) {
        if (cursor.isFirstPage()) {
            return "";
        }
        String comparator = cursor.getSortDirection().isAscending() ? ">" : "<";
        return " AND (" + sortColumn + " " + comparator + " :cursorLastSortValue OR ("
                + sortColumn + " = :cursorLastSortValue AND " + idColumn + " " + comparator + " :cursorLastId))";
    }

    protected Single<R> completeEntity(R entity) {
        return Single.just(entity);
    }

    protected abstract R toEntity(T databaseRow);
    protected abstract Class<T> rowType();
    protected abstract String idColumn();
    protected abstract String sortColumn(C cursor);
    protected abstract Object sortFieldValue(C cursor);
    protected abstract C nextCursor(C currentCursor, R element);
}
