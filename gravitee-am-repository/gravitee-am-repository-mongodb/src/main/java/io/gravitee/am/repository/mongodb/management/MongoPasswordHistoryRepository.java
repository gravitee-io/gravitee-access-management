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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.PasswordHistoryRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordHistoryMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.common.utils.RandomString.generate;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_USER_ID;
@Component
public class MongoPasswordHistoryRepository extends AbstractManagementMongoRepository implements PasswordHistoryRepository {

    private MongoCollection<PasswordHistoryMongo> mongoCollection;

    @PostConstruct
    public void init() {
        mongoCollection = mongoOperations.getCollection("password_histories", PasswordHistoryMongo.class);
        super.init(mongoCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put( new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("rt1ri1"));
        indexes.put( new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("rt1ri1u1"));

        super.createIndex(mongoCollection, indexes);
    }

    @Override
    public Maybe<PasswordHistory> findById(String id) {
        return Observable.fromPublisher(mongoCollection.find(eq(FIELD_ID, id)).first()).firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PasswordHistory> create(PasswordHistory item) {
        Objects.requireNonNull(item);
        var historyMongo = convert(item);
        historyMongo.setId(historyMongo.getId() == null ? generate() : historyMongo.getId());
        return Single.fromPublisher(mongoCollection.insertOne(historyMongo)).flatMap(success -> {
            item.setId(historyMongo.getId());
            return Single.just(item)
                    .observeOn(Schedulers.computation());
        });
    }

    @Override
    public Single<PasswordHistory> update(PasswordHistory item) {
        Objects.requireNonNull(item);
        var historyMongo = convert(item);
        return Single.fromPublisher(mongoCollection.replaceOne(eq(FIELD_ID, historyMongo.getId()), historyMongo))
                .flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(mongoCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PasswordHistory> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(mongoCollection.find(and(
                eq(FIELD_REFERENCE_TYPE, referenceType.toString()),
                eq(FIELD_REFERENCE_ID, referenceId)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PasswordHistory> findUserHistory(ReferenceType referenceType, String referenceId, String userId) {
        return Flowable.fromPublisher(
                mongoCollection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.toString()),
                        eq(FIELD_REFERENCE_ID, referenceId),
                        eq(FIELD_USER_ID, userId)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return Completable.fromPublisher(mongoCollection.deleteMany(eq(FIELD_USER_ID, userId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(mongoCollection.deleteMany(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))))
                .observeOn(Schedulers.computation());
    }

    private PasswordHistoryMongo convert(PasswordHistory passwordHistory) {
        var passwordHistoryMongo = new PasswordHistoryMongo();
        passwordHistoryMongo.setId(passwordHistory.getId());
        passwordHistoryMongo.setReferenceId(passwordHistory.getReferenceId());
        passwordHistoryMongo.setReferenceType(passwordHistory.getReferenceType());
        passwordHistoryMongo.setCreatedAt(passwordHistory.getCreatedAt());
        passwordHistoryMongo.setUpdatedAt(passwordHistory.getUpdatedAt());
        passwordHistoryMongo.setPassword(passwordHistory.getPassword());
        passwordHistoryMongo.setUserId(passwordHistory.getUserId());
        return passwordHistoryMongo;
    }

    private PasswordHistory convert(PasswordHistoryMongo passwordHistoryMongo) {
        var passwordHistory = new PasswordHistory();
        passwordHistory.setId(passwordHistoryMongo.getId());
        passwordHistory.setReferenceId(passwordHistoryMongo.getReferenceId());
        passwordHistory.setReferenceType(passwordHistoryMongo.getReferenceType());
        passwordHistory.setCreatedAt(passwordHistoryMongo.getCreatedAt());
        passwordHistory.setUpdatedAt(passwordHistoryMongo.getUpdatedAt());
        passwordHistory.setPassword(passwordHistoryMongo.getPassword());
        passwordHistory.setUserId(passwordHistoryMongo.getUserId());
        return passwordHistory;
    }
}
