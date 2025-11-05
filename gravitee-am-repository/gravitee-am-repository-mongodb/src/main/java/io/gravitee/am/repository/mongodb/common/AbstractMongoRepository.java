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

import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.mongodb.exceptions.MongoRepositoryExceptionMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static java.util.Objects.requireNonNullElse;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractMongoRepository {

    private final Logger logger = LoggerFactory.getLogger(AbstractMongoRepository.class);
    protected static final String FIELD_ID = "_id";
    protected static final String FIELD_DOMAIN = "domain";
    protected static final String FIELD_CLIENT = "client";
    protected static final String FIELD_UPDATED_AT = "updatedAt";
    protected static final String FIELD_CREATED_AT = "createdAt";
    protected static final String FIELD_REFERENCE_TYPE = "referenceType";
    protected static final String FIELD_REFERENCE_ID = "referenceId";
    protected static final String FIELD_ORGANIZATION_ID = "organizationId";
    protected static final String FIELD_NAME = "name";
    protected static final String FIELD_USER_ID = "userId";
    protected static final String FIELD_USER_EXTERNAL_ID = "userExternalId";
    protected static final String FIELD_USER_SOURCE = "userSource";

    /**
     * Default UserFields for entities linked to the User.
     */
    protected static final UserIdFields DEFAULT_USER_FIELDS = new UserIdFields(FIELD_USER_ID, FIELD_USER_SOURCE, FIELD_USER_EXTERNAL_ID);

    private static final MongoRepositoryExceptionMapper exceptionMapper = new MongoRepositoryExceptionMapper();

    @Autowired
    protected FilterCriteriaParser filterCriteriaParser;

    protected void init(MongoCollection<?> collection) {
        Single.fromPublisher(collection.createIndex(new Document(FIELD_ID, 1), new IndexOptions()))
                .subscribe(
                        ignore -> logger.debug("Index {} created", FIELD_ID),
                        throwable -> logger.error("Error occurs during creation of index {}", FIELD_ID, throwable)
                );
    }

    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes, boolean ensure) {
        if (ensure) {
            var indexesModel = indexes.entrySet().stream().map(entry -> new IndexModel(entry.getKey(), entry.getValue().background(true))).toList();
            Completable.fromPublisher(collection.createIndexes(indexesModel))
                    .subscribe(() -> logger.debug("{} indexes created", indexes.size()),
                            throwable -> logger.error("An error has occurred during creation of indexes", throwable));
        }
    }

    protected <D, T> Maybe<T> findOne(MongoCollection<D> collection, Bson query, Function<D, T> convertFromMongo) {
        return Observable.fromPublisher(
                        collection
                                .find(query)
                                .limit(1)
                                .first())
                .firstElement()
                .map(convertFromMongo);
    }

    protected Bson userIdMatches(UserId user) {
       return userIdMatches(user, DEFAULT_USER_FIELDS);
    }

    protected final Bson userIdMatches(UserId userId, UserIdFields userIdFields) {
        var idField = requireNonNullElse(userIdFields.idField(), FIELD_USER_ID);
        var externalIdField = requireNonNullElse(userIdFields.externalIdField(), FIELD_USER_EXTERNAL_ID);
        var sourceField = requireNonNullElse(userIdFields.sourceField(), FIELD_USER_SOURCE);

        if (userId.id() != null && userId.hasExternal()) {
            return or(eq(idField, userId.id()), and(eq(externalIdField, userId.externalId()), eq(sourceField, userId.source())));
        } else if (userId.hasExternal()) {
            return and(eq(externalIdField, userId.externalId()), eq(sourceField, userId.source()));
        } else if (userId.id() != null) {
            return eq(idField, userId.id());
        } else {
           throw new IllegalArgumentException("attempt to search by an empty UserId");
        }
    }

    protected Completable dropIndexes(MongoCollection<?> collection, Predicate<String> nameMatcher) {
        return Observable.fromPublisher(collection.listIndexes())
                .map(document -> document.getString("name"))
                .filter(nameMatcher)
                .flatMapCompletable(indexName -> Completable
                        .fromPublisher(collection.dropIndex(indexName))
                        .doOnError(e -> logger.error("An error has occurred while deleting index {}", indexName, e)));
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
