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
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.repository.management.api.EntityStoreRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.EntityStoreMongo;
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
public class MongoEntityStoreRepository extends AbstractManagementMongoRepository implements EntityStoreRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<EntityStoreMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("entity_stores", EntityStoreMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Maybe<EntityStore> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<EntityStore> findByDomain(String domainId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN_ID, domainId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<EntityStore> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<EntityStore> create(EntityStore item) {
        EntityStoreMongo mongo = convert(item);
        mongo.setId(mongo.getId() == null ? RandomString.generate() : mongo.getId());
        return Single.fromPublisher(collection.insertOne(mongo))
                .flatMap(success -> {
                    item.setId(mongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<EntityStore> update(EntityStore item) {
        EntityStoreMongo mongo = convert(item);
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

    private EntityStore convert(EntityStoreMongo mongo) {
        if (mongo == null) return null;
        EntityStore es = new EntityStore();
        es.setId(mongo.getId());
        es.setDomainId(mongo.getDomainId());
        es.setName(mongo.getName());
        es.setLatestVersion(mongo.getLatestVersion());
        es.setCreatedAt(mongo.getCreatedAt());
        es.setUpdatedAt(mongo.getUpdatedAt());
        return es;
    }

    private EntityStoreMongo convert(EntityStore es) {
        if (es == null) return null;
        EntityStoreMongo mongo = new EntityStoreMongo();
        mongo.setId(es.getId());
        mongo.setDomainId(es.getDomainId());
        mongo.setName(es.getName());
        mongo.setLatestVersion(es.getLatestVersion());
        mongo.setCreatedAt(es.getCreatedAt());
        mongo.setUpdatedAt(es.getUpdatedAt());
        return mongo;
    }
}
