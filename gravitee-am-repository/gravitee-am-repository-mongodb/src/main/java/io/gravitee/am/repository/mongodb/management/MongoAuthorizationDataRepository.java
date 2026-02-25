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
import io.gravitee.am.model.AuthorizationData;
import io.gravitee.am.repository.management.api.AuthorizationDataRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationDataMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationDataRepository extends AbstractManagementMongoRepository implements AuthorizationDataRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<AuthorizationDataMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_data", AuthorizationDataMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1").unique(true)));
    }

    @Override
    public Maybe<AuthorizationData> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationData> findByDomain(String domainId) {
        return Observable.fromPublisher(collection.find(eq(FIELD_DOMAIN_ID, domainId)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationData> create(AuthorizationData item) {
        AuthorizationDataMongo data = convert(item);
        data.setId(data.getId() == null ? RandomString.generate() : data.getId());
        return Single.fromPublisher(collection.insertOne(data))
                .flatMap(success -> {
                    item.setId(data.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationData> update(AuthorizationData item) {
        AuthorizationDataMongo data = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, data.getId()), data))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN_ID, domainId)))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationData convert(AuthorizationDataMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationData data = new AuthorizationData();
        data.setId(mongo.getId());
        data.setDomainId(mongo.getDomainId());
        data.setEngineType(mongo.getEngineType());
        data.setContent(mongo.getContent());
        data.setVersion(mongo.getVersion());
        data.setCreatedAt(mongo.getCreatedAt());
        data.setUpdatedAt(mongo.getUpdatedAt());

        return data;
    }

    private AuthorizationDataMongo convert(AuthorizationData data) {
        if (data == null) {
            return null;
        }

        AuthorizationDataMongo mongo = new AuthorizationDataMongo();
        mongo.setId(data.getId());
        mongo.setDomainId(data.getDomainId());
        mongo.setEngineType(data.getEngineType());
        mongo.setContent(data.getContent());
        mongo.setVersion(data.getVersion());
        mongo.setCreatedAt(data.getCreatedAt());
        mongo.setUpdatedAt(data.getUpdatedAt());

        return mongo;
    }
}
