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

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResource.Type;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ProtectedResourceMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.mongodb.client.model.Filters.*;
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
        indexes.put(new Document(DOMAIN_ID_FIELD, 1).append(RESOURCE_IDENTIFIERS_FIELD, 1), new IndexOptions().name("d1ri1"));

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

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domain, Type type, PageSortRequest pageSortRequest) {
        Bson query = and(eq(DOMAIN_ID_FIELD, domain), eq(TYPE_FIELD, type));
        return queryProtectedResource(query, pageSortRequest).observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, Type type, List<String> ids, PageSortRequest pageSortRequest) {
        Bson query = and(eq(DOMAIN_ID_FIELD, domain), eq(TYPE_FIELD, type), in(FIELD_ID, ids));
        return queryProtectedResource(query, pageSortRequest).observeOn(Schedulers.computation());
    }

    @Override
    public Single<Boolean> existsByResourceIdentifiers(String domainId, List<String> resourceIdentifiers) {
        if(resourceIdentifiers.isEmpty()){
            return Single.just(false);
        }
        return Single.fromPublisher(collection.countDocuments(and(eq(DOMAIN_ID_FIELD, domainId), in(RESOURCE_IDENTIFIERS_FIELD, resourceIdentifiers))))
                .map(count -> count > 0);
    }

    private Single<Page<ProtectedResourcePrimaryData>> queryProtectedResource(Bson query, PageSortRequest pageSortRequest) {
        Single<Long> countOperation = Observable.fromPublisher(collection
                        .countDocuments(query, countOptions()))
                .firstElement()
                .toSingle();
        String sortBy = pageSortRequest.getSortBy().orElse(UPDATED_AT_FIELD);
        Single<List<ProtectedResourcePrimaryData>> operation = Observable.fromPublisher(
                        withMaxTime(collection.find(query))
                                .sort(new BasicDBObject(sortBy, pageSortRequest.direction()))
                                .skip(pageSortRequest.skip()).limit(pageSortRequest.getSize()))
                .map(this::convert)
                .map(ProtectedResourcePrimaryData::of)
                .collect(ArrayList::new, List::add);
        return Single.zip(countOperation, operation, (count, elements) -> new Page<>(elements, pageSortRequest.getPage(), count));
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
        result.setType(Type.valueOf(mongo.getType()));
        result.setCreatedAt(mongo.getCreatedAt());
        result.setUpdatedAt(mongo.getUpdatedAt());
        return result;
    }
}
