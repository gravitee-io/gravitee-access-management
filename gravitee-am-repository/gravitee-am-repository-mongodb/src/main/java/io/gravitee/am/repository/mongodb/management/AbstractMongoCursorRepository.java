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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.micrometer.common.util.StringUtils;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.conversions.Bson;

import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

public abstract class AbstractMongoCursorRepository<T, R, C extends CursorRequest> extends AbstractManagementMongoRepository {

    protected Single<CursorPage<R, C>> findCursorPage(
            MongoCollection<T> collection,
            Bson baseFilter,
            C cursor,
            int limit) {

        String sortFieldName = sortFieldName(cursor);
        Bson cursorFilter = filterWithCursorSort(baseFilter, sortFieldName, cursor);
        Bson sort = new BasicDBObject(sortFieldName, cursor.getSortDirection().toInt()).append(FIELD_ID, cursor.getSortDirection().toInt());
        boolean useCollation = useCollation(cursor);

        Single<Long> countOperation = countItems(collection, cursorFilter, useCollation ? countOptionsWithCollation() : countOptions());
        FindPublisher<T> findPublisher = withMaxTime(collection.find(cursorFilter)).sort(sort);
        if (useCollation) {
            findPublisher = findPublisher.collation(CASE_INSENSITIVE_COLLATION);
        }

        Single<List<R>> dataOperation;
        if(cursor.isFirstPage()) {
            dataOperation = Observable.fromPublisher(
                            findPublisher
                                    .skip(cursor.getPage() * limit)
                                    .limit(limit + 1))
                    .map(this::toModel)
                    .toList();
        } else {
            dataOperation = Observable.fromPublisher(
                            findPublisher
                                    .limit(limit + 1))
                    .map(this::toModel)
                    .toList();
        }

        return Single.zip(countOperation, dataOperation, (totalCount, items) -> {
                    boolean hasNext = items.size() > limit;
                    long normalizedTotalCount = cursor.isFirstPage() ? totalCount : totalCount + ((long) cursor.getPage() * limit);
                    List<R> data = hasNext ? items.subList(0, limit) : items;
                    if (hasNext && !data.isEmpty()) {
                        R last = data.get(data.size() - 1);
                        C c = nextCursor(cursor, last);
                        return new CursorPage<R, C>(data, c, normalizedTotalCount);
                    } else {
                        return new CursorPage<R, C>(data, null, normalizedTotalCount);
                    }
                })
                .observeOn(Schedulers.computation());
    }

    private Bson filterWithCursorSort(Bson filter, String sortFieldName, C cursor) {
        if (cursor.isFirstPage()) {
            return filter;
        } else {
            return and(filter, filterWithCursorSort(cursor, sortFieldName));
        }
    }

    private Bson filterWithCursorSort(CursorRequest cursor,
                                      String sortFieldName) {
        String compareOp = cursor.getSortDirection().isAscending() ? "$gt" : "$lt";
        Object sortValue = sortFieldValue(cursor);
        Bson beyondSort = new BasicDBObject(sortFieldName, new BasicDBObject(compareOp, sortValue));
        Bson sameSortBeyondId = and(
                eq(sortFieldName, sortValue),
                new BasicDBObject(FIELD_ID, new BasicDBObject(compareOp, cursor.getLastId()))
        );
        return or(beyondSort, sameSortBeyondId);
    }


    abstract protected R toModel(T databaseModel);
    abstract protected Bson withQuery(Bson base, String query);
    abstract protected String sortFieldName(CursorRequest cursor);
    abstract protected Object sortFieldValue(CursorRequest cursor);
    abstract protected boolean useCollation(C cursor);
    abstract protected C nextCursor(C currentCursor, R element);


}
