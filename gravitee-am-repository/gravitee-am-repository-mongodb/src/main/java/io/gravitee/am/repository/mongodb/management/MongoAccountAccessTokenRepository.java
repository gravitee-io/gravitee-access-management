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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.AccountAccessTokenRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AccountAccessTokenMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_USER_ID;

@Repository
public class MongoAccountAccessTokenRepository extends AbstractManagementMongoRepository implements AccountAccessTokenRepository {
    public static final String COLLECTION_NAME = "account_access_tokens";

    private MongoCollection<AccountAccessTokenMongo> accountTokensCollection;

    @PostConstruct
    public void init() {
        accountTokensCollection = mongoOperations.getCollection(COLLECTION_NAME, AccountAccessTokenMongo.class);
        super.init(accountTokensCollection);
        createIndex(accountTokensCollection, Map.of(new Document(FIELD_USER_ID, 1), new IndexOptions().name("u1")));
    }


    @Override
    public Maybe<AccountAccessToken> findById(String tokenId) {
        return Observable.fromPublisher(accountTokensCollection.find(eq(FIELD_ID, tokenId))
                        .first())
                .firstElement()
                .map(this::convert).observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AccountAccessToken> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Flowable.fromPublisher(accountTokensCollection.find(and(
                        eq(FIELD_USER_ID, userId),
                        eq(FIELD_REFERENCE_ID, referenceId),
                        eq(FIELD_REFERENCE_TYPE, referenceType.name())
                )))
                .map(this::convert).observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Completable.fromPublisher(accountTokensCollection.deleteMany(and(
                eq(FIELD_USER_ID, userId),
                eq(FIELD_REFERENCE_ID, referenceId),
                eq(FIELD_REFERENCE_TYPE, referenceType.name())
        ))).observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccountAccessToken> create(AccountAccessToken item) {
        var document = convert(item);
        return Single.fromPublisher(accountTokensCollection.insertOne(document))
                .flatMap(success -> findById(document.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccountAccessToken> update(AccountAccessToken item) {
        var document = convert(item);
        return Single.fromPublisher(accountTokensCollection.replaceOne(eq(FIELD_ID, document.getId()), document))
                .flatMap(success -> findById(document.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String tokenId) {
        return Completable.fromPublisher(accountTokensCollection.deleteOne(eq(FIELD_ID, tokenId)))
                .observeOn(Schedulers.computation());
    }

    private AccountAccessToken convert(AccountAccessTokenMongo mongo) {
        return AccountAccessToken.builder()
                .tokenId(mongo.getId())
                .referenceType(ReferenceType.valueOf(mongo.getReferenceType()))
                .referenceId(mongo.getReferenceId())
                .userId(mongo.getUserId())
                .issuerId(mongo.getIssuerId())
                .name(mongo.getName())
                .token(mongo.getToken())
                .createdAt(mongo.getCreatedAt())
                .build();
    }

    private AccountAccessTokenMongo convert(AccountAccessToken entity) {
        var mongo = new AccountAccessTokenMongo();
        var now = Date.from(Instant.now());
        mongo.setId(Objects.requireNonNullElseGet(entity.tokenId(), RandomString::generate));
        mongo.setUserId(entity.userId());
        mongo.setIssuerId(entity.issuerId());
        mongo.setReferenceType(entity.referenceType().name());
        mongo.setReferenceId(entity.referenceId());
        mongo.setName(entity.name());
        mongo.setToken(entity.token());
        mongo.setCreatedAt(now);
        mongo.setUpdatedAt(now);
        return mongo;
    }

}
