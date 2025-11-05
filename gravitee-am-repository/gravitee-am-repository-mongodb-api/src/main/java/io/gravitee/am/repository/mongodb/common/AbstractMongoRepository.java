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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.mongodb.exceptions.MongoRepositoryExceptionMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Map;

import static io.gravitee.am.repository.mongodb.common.MongoUtils.DEFAULT_USER_FIELDS;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractMongoRepository {

    private static final MongoRepositoryExceptionMapper exceptionMapper = new MongoRepositoryExceptionMapper();

    protected void init(MongoCollection<?> collection) {
        MongoUtils.init(collection);
    }

    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes, boolean ensure) {
        MongoUtils.createIndex(collection, indexes, ensure);
    }

    protected <D, T> Maybe<T> findOne(MongoCollection<D> collection, Bson query, Function<D, T> convertFromMongo) {
        return MongoUtils.findOne(collection, query, convertFromMongo);
    }

    protected Bson userIdMatches(UserId user) {
       return userIdMatches(user, DEFAULT_USER_FIELDS);
    }

    protected final Bson userIdMatches(UserId userId, UserIdFields userIdFields) {
       return MongoUtils.userIdMatches(userId, userIdFields);
    }

    protected Completable dropIndexes(MongoCollection<?> collection, Predicate<String> nameMatcher) {
        return MongoUtils.dropIndexes(collection, nameMatcher);
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
