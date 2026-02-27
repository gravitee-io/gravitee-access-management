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
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationBundleMongo;
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
public class MongoAuthorizationBundleRepository extends AbstractManagementMongoRepository implements AuthorizationBundleRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<AuthorizationBundleMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_bundles", AuthorizationBundleMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Maybe<AuthorizationBundle> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationBundle> findByDomain(String domainId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN_ID, domainId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationBundle> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationBundle> create(AuthorizationBundle item) {
        AuthorizationBundleMongo bundle = convert(item);
        bundle.setId(bundle.getId() == null ? RandomString.generate() : bundle.getId());
        return Single.fromPublisher(collection.insertOne(bundle))
                .flatMap(success -> {
                    item.setId(bundle.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationBundle> update(AuthorizationBundle item) {
        AuthorizationBundleMongo bundle = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, bundle.getId()), bundle))
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

    private AuthorizationBundle convert(AuthorizationBundleMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId(mongo.getId());
        bundle.setDomainId(mongo.getDomainId());
        bundle.setName(mongo.getName());
        bundle.setDescription(mongo.getDescription());
        bundle.setEngineType(mongo.getEngineType());
        bundle.setPolicySetId(mongo.getPolicySetId());
        bundle.setPolicySetVersion(mongo.getPolicySetVersion());
        bundle.setPolicySetPinToLatest(mongo.isPolicySetPinToLatest());
        bundle.setSchemaId(mongo.getSchemaId());
        bundle.setSchemaVersion(mongo.getSchemaVersion());
        bundle.setSchemaPinToLatest(mongo.isSchemaPinToLatest());
        bundle.setEntityStoreId(mongo.getEntityStoreId());
        bundle.setEntityStoreVersion(mongo.getEntityStoreVersion());
        bundle.setEntityStorePinToLatest(mongo.isEntityStorePinToLatest());
        bundle.setCreatedAt(mongo.getCreatedAt());
        bundle.setUpdatedAt(mongo.getUpdatedAt());

        return bundle;
    }

    private AuthorizationBundleMongo convert(AuthorizationBundle bundle) {
        if (bundle == null) {
            return null;
        }

        AuthorizationBundleMongo mongo = new AuthorizationBundleMongo();
        mongo.setId(bundle.getId());
        mongo.setDomainId(bundle.getDomainId());
        mongo.setName(bundle.getName());
        mongo.setDescription(bundle.getDescription());
        mongo.setEngineType(bundle.getEngineType());
        mongo.setPolicySetId(bundle.getPolicySetId());
        mongo.setPolicySetVersion(bundle.getPolicySetVersion());
        mongo.setPolicySetPinToLatest(bundle.isPolicySetPinToLatest());
        mongo.setSchemaId(bundle.getSchemaId());
        mongo.setSchemaVersion(bundle.getSchemaVersion());
        mongo.setSchemaPinToLatest(bundle.isSchemaPinToLatest());
        mongo.setEntityStoreId(bundle.getEntityStoreId());
        mongo.setEntityStoreVersion(bundle.getEntityStoreVersion());
        mongo.setEntityStorePinToLatest(bundle.isEntityStorePinToLatest());
        mongo.setCreatedAt(bundle.getCreatedAt());
        mongo.setUpdatedAt(bundle.getUpdatedAt());

        return mongo;
    }
}
