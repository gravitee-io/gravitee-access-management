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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationIdentityProviderMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ProtectedResourceMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.management.MongoApplicationRepository.*;
import static io.gravitee.am.repository.mongodb.management.internal.model.ProtectedResourceMongo.*;

@Component
public class MongoProtectedResourceRepository extends AbstractManagementMongoRepository implements ProtectedResourceRepository {
    public static final String COLLECTION_NAME = "protected_resources";

    private MongoCollection<ProtectedResourceMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, ProtectedResourceMongo.class);
        super.init(collection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(DOMAIN_ID_FIELD, 1).append(CLIENT_ID_FIELD, 1), new IndexOptions().name("d1ci1"));
        indexes.put(new Document(DOMAIN_ID_FIELD, 1).append(TYPE_FIELD, 1).append(UPDATED_AT_FIELD, -1), new IndexOptions().name("d1t1ua_1"));

        super.createIndex(collection, indexes);
    }

    @Override
    public Maybe<ProtectedResource> findById(String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ProtectedResource> create(ProtectedResource item) {
        ProtectedResourceMongo protectedResource = convert(item);
        protectedResource.setId(item.getId() == null ? RandomString.generate() : item.getId());
        return Single.fromPublisher(collection.insertOne(protectedResource)).flatMap(success -> {
                    item.setId(protectedResource.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ProtectedResource> update(ProtectedResource item) {
        return Single.just(item); // TODO AM-5756
    }

    @Override
    public Completable delete(String s) {
        return Completable.complete(); // TODO AM-5757
    }

    @Override
    public Maybe<ProtectedResource> findByDomainAndClient(String domainId, String clientId) {
        return Observable.fromPublisher(collection.find(and(eq(DOMAIN_ID_FIELD, domainId), eq(CLIENT_ID_FIELD, clientId))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private ProtectedResourceMongo convert(ProtectedResource other) {
        ProtectedResourceMongo mongo = new ProtectedResourceMongo();
        mongo.setId(other.getId());
        mongo.setName(other.getName());
        mongo.setClientId(other.getClientId());
        mongo.setDomainId(other.getDomainId());
        mongo.setResourceIdentifiers(other.getResourceIdentifiers());
        mongo.setDescription(other.getDescription());
        mongo.setClientSecrets(convertToClientSecretMongo(other.getClientSecrets()));
        mongo.setSecretSettings(convertToSecretSettingsMongo(other.getSecretSettings()));
        mongo.setType(other.getType().toString());
        mongo.setCreatedAt(other.getCreatedAt());
        mongo.setUpdatedAt(other.getUpdatedAt());
        return mongo;
    }

    private ProtectedResource convert(ProtectedResourceMongo mongo) {
        ProtectedResource result = new ProtectedResource();
        result.setId(mongo.getId());
        result.setName(mongo.getName());
        result.setClientId(mongo.getClientId());
        result.setDomainId(mongo.getDomainId());
        result.setResourceIdentifiers(mongo.getResourceIdentifiers());
        result.setDescription(mongo.getDescription());
        result.setClientSecrets(convertToClientSecret(mongo.getClientSecrets()));
        result.setSecretSettings(convertToSecretSettings(mongo.getSecretSettings()));
        result.setType(ProtectedResource.Type.valueOf(mongo.getType()));
        result.setCreatedAt(mongo.getCreatedAt());
        result.setUpdatedAt(mongo.getUpdatedAt());
        return result;
    }
}
