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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.PasswordHistoryRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordHistoryMongo;
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.common.utils.RandomString.generate;

@Component
public class MongoPasswordHistoryRepository extends AbstractManagementMongoRepository implements PasswordHistoryRepository {

    private MongoCollection<PasswordHistoryMongo> mongoCollection;

    @PostConstruct
    public void init() {
        mongoCollection = mongoOperations.getCollection("password_histories", PasswordHistoryMongo.class);
        super.init(mongoCollection);
        super.createIndex(mongoCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(mongoCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1)
                .append(FIELD_USER_ID, 1));
    }

    @Override
    public Maybe<PasswordHistory> findById(String id) {
        return Observable.fromPublisher(mongoCollection.find(eq(FIELD_ID, id)).first()).firstElement()
                .map(this::convert);
    }

    @Override
    public Single<PasswordHistory> create(PasswordHistory item) {
        Objects.requireNonNull(item);
        var historyMongo = convert(item);
        historyMongo.setId(historyMongo.getId() == null ? generate() : historyMongo.getId());
        return Single.fromPublisher(mongoCollection.insertOne(historyMongo)).flatMap(success -> {
            item.setId(historyMongo.getId());
            return Single.just(item);
        });
    }

    @Override
    public Single<PasswordHistory> update(PasswordHistory item) {
        Objects.requireNonNull(item);
        var historyMongo = convert(item);
        return Single.fromPublisher(mongoCollection.replaceOne(eq(FIELD_ID, historyMongo.getId()), historyMongo))
                .flatMap(success -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(mongoCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Flowable<PasswordHistory> findUserHistory(ReferenceType referenceType, String referenceId, String userId) {
        return Flowable.fromPublisher(
                mongoCollection.find(and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.toString()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USER_ID, userId)))).map(this::convert);
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
