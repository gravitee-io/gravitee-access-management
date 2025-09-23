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

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.UserIdFields;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import lombok.experimental.UtilityClass;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static java.util.Objects.requireNonNullElse;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@UtilityClass
public final class MongoUtils {

    private static final Logger logger = LoggerFactory.getLogger(MongoUtils.class);

    public static final String FIELD_ID = "_id";
    public static final String FIELD_DOMAIN = "domain";
    public static final String FIELD_CLIENT = "client";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_REFERENCE_TYPE = "referenceType";
    public static final String FIELD_REFERENCE_ID = "referenceId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USER_EXTERNAL_ID = "userExternalId";
    public static final String FIELD_USER_SOURCE = "userSource";

    /**
     * Default UserFields for entities linked to the User.
     */
    public static final UserIdFields DEFAULT_USER_FIELDS = new UserIdFields(FIELD_USER_ID, FIELD_USER_SOURCE, FIELD_USER_EXTERNAL_ID);

    public static void init(MongoCollection<?> collection) {
        Completable.fromPublisher(collection.createIndex(new Document(FIELD_ID, 1), new IndexOptions()))
                .subscribe(
                        () -> logger.debug("Index {} of collection {} created", FIELD_ID, collection.getNamespace().getCollectionName()),
                        throwable -> logger.error("Error occurred during creation of id index of {}", collection.getNamespace().getCollectionName(), throwable)
                );
    }

    public static void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes, boolean ensure) {
        if (ensure) {
            var indexesModel = indexes.entrySet()
                    .stream()
                    .map(entry -> new IndexModel(entry.getKey(), entry.getValue().background(true)))
                    .toList();
            Completable.fromPublisher(collection.createIndexes(indexesModel))
                    .retryWhen(th -> th
                            .zipWith(Flowable.range(1, 3), (error, attempt) -> attempt) // 3 attempts
                            .flatMap(attempt -> {
                                logger.debug("Retrying index creation for {}, attempt={}/3", collection.getNamespace().getCollectionName(), attempt);
                                return Flowable.timer(attempt, TimeUnit.SECONDS); // delayed backoff
                            }))
                    .subscribe(
                            () -> logger.debug("{} indexes created", collection.getNamespace().getCollectionName()),
                            throwable -> logger.error("Error occurred during indexes creation for {}", collection.getNamespace().getCollectionName(), throwable)
                    );

        }
    }

    public static <D, T> Maybe<T> findOne(MongoCollection<D> collection, Bson query, Function<D, T> convertFromMongo) {
        return Observable.fromPublisher(
                        collection
                                .find(query)
                                .limit(1)
                                .first())
                .firstElement()
                .map(convertFromMongo);
    }

    public static Bson userIdMatches(UserId userId, UserIdFields userIdFields) {
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

    public static Completable dropIndexes(MongoCollection<?> collection, Predicate<String> nameMatcher) {
        logger.debug("Dropping indexes for collection {}", collection.getNamespace().getCollectionName());
        return Observable.fromPublisher(collection.listIndexes())
                .map(document -> document.getString("name"))
                .filter(nameMatcher)
                .doOnNext(indexName -> logger.debug("Found index to drop: {}", indexName))
                .flatMapCompletable(indexName -> Completable
                        .fromPublisher(collection.dropIndex(indexName))
                        .onErrorResumeNext(error -> {
                            if (error instanceof MongoCommandException mongoError && mongoError.getErrorCode() == 27) {
                                logger.debug("Index {} was already deleted by another process", indexName);
                                return Completable.complete();
                            }
                            return Completable.error(error);
                        })
                        .retryWhen(th -> th
                                .zipWith(Flowable.range(1, 3), (error, attempt) -> new RetryContext(error, attempt))
                                .flatMap(context -> {
                                    if (context.error() instanceof MongoCommandException mongoError && 
                                        mongoError.getErrorCode() == 12587) {
                                        logger.debug("Retrying index deletion for {} due to background operation, attempt={}/3", 
                                                   collection.getNamespace().getCollectionName(), context.attempt());
                                        return Flowable.timer(context.attempt() * 2, TimeUnit.SECONDS); // exponential backoff
                                    }
                                    return Flowable.error(context.error());
                                }))
                        .doOnError(e -> logger.error("An error has occurred while deleting index {}", indexName, e))
                        .doOnComplete(() -> logger.debug("Successfully deleted index {}", indexName)))
                .toFlowable()
                .defaultIfEmpty(Completable.complete())
                .ignoreElements()
                .doOnComplete(() -> logger.debug("Index drop operation completed for collection {}", collection.getNamespace().getCollectionName()));
    }

    public static Bson toBsonFilter(String name, Optional<?> optional) {

        return optional.map(value -> {
            if (value instanceof Enum) {
                return eq(name, ((Enum<?>) value).name());
            }

            if (value instanceof Collection) {
                if (((Collection<?>) value).isEmpty()) {
                    return null;
                }
                return in(name, (Collection<?>) value);
            }

            return eq(name, value);
        }).orElse(null);
    }

    public static Maybe<Bson> toBsonFilter(boolean logicalOr, Bson... filter) {

        List<Bson> filterCriteria = Stream.of(filter).filter(Objects::nonNull).collect(Collectors.toList());

        if (filterCriteria.isEmpty()) {
            return Maybe.empty();
        }

        if (logicalOr) {
            return Maybe.just(or(filterCriteria));
        } else {
            return Maybe.just(and(filterCriteria));
        }
    }

    record RetryContext(Throwable error, int attempt) {}
}
