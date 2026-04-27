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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainMongo;
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
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoTrustDomainRepository extends AbstractManagementMongoRepository implements TrustDomainRepository {

    private static final String COLLECTION_NAME = "trust_domains";
    private static final String FIELD_NAME = "name";

    private MongoCollection<TrustDomainMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, TrustDomainMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_NAME, 1),
                new IndexOptions().name("rt1ri1n1").unique(true)
        ));
    }

    @Override
    public Maybe<TrustDomain> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> create(TrustDomain item) {
        TrustDomainMongo doc = toMongo(item);
        doc.setId(doc.getId() == null ? RandomString.generate() : doc.getId());
        return Single.fromPublisher(collection.insertOne(doc))
                .map(success -> {
                    item.setId(doc.getId());
                    return item;
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> update(TrustDomain item) {
        TrustDomainMongo doc = toMongo(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, doc.getId()), doc))
                .map(updateResult -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<TrustDomain> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId))))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustDomain> findByName(ReferenceType referenceType, String referenceId, String name) {
        return Observable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId),
                        eq(FIELD_NAME, name))).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    private TrustDomain toEntity(TrustDomainMongo doc) {
        if (doc == null) {
            return null;
        }
        TrustDomain td = new TrustDomain();
        td.setId(doc.getId());
        td.setReferenceId(doc.getReferenceId());
        td.setReferenceType(doc.getReferenceType() != null ? ReferenceType.valueOf(doc.getReferenceType()) : null);
        td.setName(doc.getName());
        td.setDescription(doc.getDescription());
        td.setBundleSource(doc.getBundleSource() != null ? SpiffeBundleSource.valueOf(doc.getBundleSource()) : null);
        td.setJwksUrl(doc.getJwksUrl());
        td.setRefreshIntervalSeconds(doc.getRefreshIntervalSeconds());
        td.setAllowedAlgorithms(doc.getAllowedAlgorithms());
        td.setCreatedAt(doc.getCreatedAt());
        td.setUpdatedAt(doc.getUpdatedAt());
        return td;
    }

    private TrustDomainMongo toMongo(TrustDomain td) {
        if (td == null) {
            return null;
        }
        TrustDomainMongo doc = new TrustDomainMongo();
        doc.setId(td.getId());
        doc.setReferenceId(td.getReferenceId());
        doc.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        doc.setName(td.getName());
        doc.setDescription(td.getDescription());
        doc.setBundleSource(td.getBundleSource() != null ? td.getBundleSource().name() : null);
        doc.setJwksUrl(td.getJwksUrl());
        doc.setRefreshIntervalSeconds(td.getRefreshIntervalSeconds());
        doc.setAllowedAlgorithms(td.getAllowedAlgorithms());
        doc.setCreatedAt(td.getCreatedAt());
        doc.setUpdatedAt(td.getUpdatedAt());
        return doc;
    }
}
