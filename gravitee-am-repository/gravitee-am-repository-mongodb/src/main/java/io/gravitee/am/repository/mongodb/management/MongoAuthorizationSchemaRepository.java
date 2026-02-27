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
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationSchemaMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationSchemaRepository extends AbstractManagementMongoRepository implements AuthorizationSchemaRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<AuthorizationSchemaMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_schemas", AuthorizationSchemaMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Maybe<AuthorizationSchema> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationSchema> findByDomain(String domainId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN_ID, domainId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationSchema> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationSchema> create(AuthorizationSchema item) {
        AuthorizationSchemaMongo mongo = convert(item);
        mongo.setId(mongo.getId() == null ? RandomString.generate() : mongo.getId());
        return Single.fromPublisher(collection.insertOne(mongo))
                .flatMap(success -> {
                    item.setId(mongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationSchema> update(AuthorizationSchema item) {
        AuthorizationSchemaMongo mongo = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, mongo.getId()), mongo))
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

    private AuthorizationSchema convert(AuthorizationSchemaMongo mongo) {
        if (mongo == null) return null;
        AuthorizationSchema s = new AuthorizationSchema();
        s.setId(mongo.getId());
        s.setDomainId(mongo.getDomainId());
        s.setName(mongo.getName());
        s.setLatestVersion(mongo.getLatestVersion());
        s.setCreatedAt(mongo.getCreatedAt());
        s.setUpdatedAt(mongo.getUpdatedAt());
        return s;
    }

    private AuthorizationSchemaMongo convert(AuthorizationSchema s) {
        if (s == null) return null;
        AuthorizationSchemaMongo mongo = new AuthorizationSchemaMongo();
        mongo.setId(s.getId());
        mongo.setDomainId(s.getDomainId());
        mongo.setName(s.getName());
        mongo.setLatestVersion(s.getLatestVersion());
        mongo.setCreatedAt(s.getCreatedAt());
        mongo.setUpdatedAt(s.getUpdatedAt());
        return mongo;
    }
}
