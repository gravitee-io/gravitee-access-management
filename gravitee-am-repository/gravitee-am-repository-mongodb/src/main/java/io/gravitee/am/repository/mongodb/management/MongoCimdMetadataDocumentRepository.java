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
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.repository.management.api.CimdMetadataDocumentRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.CimdMetadataDocumentMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;

/**
 * MongoDB repository for cached Client ID Metadata documents (per domain).
 * Expiry is enforced in two ways: {@link #purgeExpiredData()} runs on the management purge schedule, and a TTL index on {@code expiresAt}.
 *
 * @author GraviteeSource Team
 */
@Component
public class MongoCimdMetadataDocumentRepository extends AbstractManagementMongoRepository implements CimdMetadataDocumentRepository {

    private static final String COLLECTION = "cimd_metadata_documents";
    private static final String FIELD_DOMAIN_ID = "domainId";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private MongoCollection<CimdMetadataDocumentMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION, CimdMetadataDocumentMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_DOMAIN_ID, 1).append(FIELD_CLIENT_ID, 1),
                new IndexOptions().name("di1ci1").unique(true)
        ));
        super.createIndex(collection, Map.of(
                new Document(FIELD_EXPIRES_AT, 1),
                new IndexOptions().name("ea1").expireAfter(0L, TimeUnit.SECONDS)
        ));
    }

    @Override
    public Maybe<CimdMetadataDocument> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CimdMetadataDocument> findByDomainAndClientId(String domainId, String clientId) {
        return Observable.fromPublisher(
                        collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_CLIENT_ID, clientId))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CimdMetadataDocument> findByDomain(String domainId) {
        return Flowable.fromPublisher(collection.find(eq(FIELD_DOMAIN_ID, domainId)))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdMetadataDocument> create(CimdMetadataDocument item) {
        CimdMetadataDocumentMongo doc = convert(item);
        doc.setId(doc.getId() == null ? RandomString.generate() : doc.getId());
        return Single.fromPublisher(collection.insertOne(doc))
                .flatMap(success -> { item.setId(doc.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdMetadataDocument> update(CimdMetadataDocument item) {
        CimdMetadataDocumentMongo doc = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, doc.getId()), doc))
                .flatMap(result -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndClientId(String domainId, String clientId) {
        return Completable.fromPublisher(
                        collection.deleteOne(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_CLIENT_ID, clientId))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        return Completable.fromPublisher(collection.deleteMany(lt(FIELD_EXPIRES_AT, new Date())))
                .observeOn(Schedulers.computation());
    }

    private CimdMetadataDocument convert(CimdMetadataDocumentMongo mongo) {
        if (mongo == null) return null;
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setId(mongo.getId());
        doc.setDomainId(mongo.getDomainId());
        doc.setClientId(mongo.getClientId());
        doc.setMetadata(mongo.getMetadata());
        doc.setFetchedAt(mongo.getFetchedAt());
        doc.setExpiresAt(mongo.getExpiresAt());
        doc.setUpdatedAt(mongo.getUpdatedAt());
        return doc;
    }

    private CimdMetadataDocumentMongo convert(CimdMetadataDocument doc) {
        if (doc == null) return null;
        CimdMetadataDocumentMongo mongo = new CimdMetadataDocumentMongo();
        mongo.setId(doc.getId());
        mongo.setDomainId(doc.getDomainId());
        mongo.setClientId(doc.getClientId());
        mongo.setMetadata(doc.getMetadata());
        mongo.setFetchedAt(doc.getFetchedAt());
        mongo.setExpiresAt(doc.getExpiresAt());
        mongo.setUpdatedAt(doc.getUpdatedAt());
        return mongo;
    }
}
