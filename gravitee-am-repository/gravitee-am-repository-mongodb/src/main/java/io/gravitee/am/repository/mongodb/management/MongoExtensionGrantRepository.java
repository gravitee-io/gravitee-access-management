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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ExtensionGrantMongo;
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

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_NAME;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoExtensionGrantRepository extends AbstractManagementMongoRepository implements ExtensionGrantRepository {

    private MongoCollection<ExtensionGrantMongo> extensionGrantsCollection;

    @PostConstruct
    public void init() {
        extensionGrantsCollection = mongoOperations.getCollection("extension_grants", ExtensionGrantMongo.class);
        super.init(extensionGrantsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_DOMAIN, 1), new IndexOptions().name("d1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_NAME, 1), new IndexOptions().name("d1n1"));

        super.createIndex(extensionGrantsCollection,indexes);
    }

    @Override
    public Flowable<ExtensionGrant> findByDomain(String domain) {
        return Flowable.fromPublisher(extensionGrantsCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<ExtensionGrant> findByDomainAndName(String domain, String name) {
        return Observable.fromPublisher(extensionGrantsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_NAME, name))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<ExtensionGrant> findById(String tokenGranterId) {
        return Observable.fromPublisher(extensionGrantsCollection.find(eq(FIELD_ID, tokenGranterId)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ExtensionGrant> create(ExtensionGrant item) {
        ExtensionGrantMongo extensionGrant = convert(item);
        extensionGrant.setId(extensionGrant.getId() == null ? RandomString.generate() : extensionGrant.getId());
        return Single.fromPublisher(extensionGrantsCollection.insertOne(extensionGrant)).flatMap(success -> { item.setId(extensionGrant.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ExtensionGrant> update(ExtensionGrant item) {
        ExtensionGrantMongo extensionGrant = convert(item);
        return Single.fromPublisher(extensionGrantsCollection.replaceOne(eq(FIELD_ID, extensionGrant.getId()), extensionGrant)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(extensionGrantsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private ExtensionGrant convert(ExtensionGrantMongo extensionGrantMongo) {
        if (extensionGrantMongo == null) {
            return null;
        }

        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId(extensionGrantMongo.getId());
        extensionGrant.setName(extensionGrantMongo.getName());
        extensionGrant.setType(extensionGrantMongo.getType());
        extensionGrant.setConfiguration(extensionGrantMongo.getConfiguration());
        extensionGrant.setDomain(extensionGrantMongo.getDomain());
        extensionGrant.setGrantType(extensionGrantMongo.getGrantType());
        extensionGrant.setIdentityProvider(extensionGrantMongo.getIdentityProvider());
        extensionGrant.setCreateUser(extensionGrantMongo.isCreateUser());
        extensionGrant.setUserExists(extensionGrantMongo.isUserExists());
        extensionGrant.setCreatedAt(extensionGrantMongo.getCreatedAt());
        extensionGrant.setUpdatedAt(extensionGrantMongo.getUpdatedAt());
        return extensionGrant;
    }

    private ExtensionGrantMongo convert(ExtensionGrant extensionGrant) {
        if (extensionGrant == null) {
            return null;
        }

        ExtensionGrantMongo extensionGrantMongo = new ExtensionGrantMongo();
        extensionGrantMongo.setId(extensionGrant.getId());
        extensionGrantMongo.setName(extensionGrant.getName());
        extensionGrantMongo.setType(extensionGrant.getType());
        extensionGrantMongo.setConfiguration(extensionGrant.getConfiguration());
        extensionGrantMongo.setDomain(extensionGrant.getDomain());
        extensionGrantMongo.setGrantType(extensionGrant.getGrantType());
        extensionGrantMongo.setIdentityProvider(extensionGrant.getIdentityProvider());
        extensionGrantMongo.setCreateUser(extensionGrant.isCreateUser());
        extensionGrantMongo.setUserExists(extensionGrant.isUserExists());
        extensionGrantMongo.setCreatedAt(extensionGrant.getCreatedAt());
        extensionGrantMongo.setUpdatedAt(extensionGrant.getUpdatedAt());
        return extensionGrantMongo;
    }
}
