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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.CimdClientStateRepository;
import io.gravitee.am.dataplane.mongodb.repository.model.CimdClientStateMongo;
import io.gravitee.am.model.CimdClientState;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoCimdClientStateRepository extends AbstractDataPlaneMongoRepository implements CimdClientStateRepository {

    private static final String COLLECTION = "cimd_client_states";
    private static final String FIELD_DOMAIN_ID = "domainId";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_MONITORED_PROPERTIES_HASH = "monitoredPropertiesHash";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private MongoCollection<CimdClientStateMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoDatabase.getCollection(COLLECTION, CimdClientStateMongo.class);
        super.createIndex(collection, Map.of(
                new Document(FIELD_DOMAIN_ID, 1).append(FIELD_CLIENT_ID, 1),
                new IndexOptions().name("di1ci1").unique(true)
        ));
    }

    @Override
    public Maybe<CimdClientState> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CimdClientState> findByDomainAndClientId(String domainId, String clientId) {
        return Observable.fromPublisher(
                        collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_CLIENT_ID, clientId))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> upsert(CimdClientState state) {
        final Document updateDoc = new Document("$set",
                new Document(FIELD_MONITORED_PROPERTIES_HASH, state.getMonitoredPropertiesHash())
                        .append(FIELD_UPDATED_AT, new Date()))
                .append("$setOnInsert",
                        new Document(FIELD_ID, RandomString.generate())
                                .append(FIELD_DOMAIN_ID, state.getDomainId())
                                .append(FIELD_CLIENT_ID, state.getClientId()));
        return Single.fromPublisher(collection.findOneAndUpdate(
                        and(eq(FIELD_DOMAIN_ID, state.getDomainId()), eq(FIELD_CLIENT_ID, state.getClientId())),
                        updateDoc,
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> create(CimdClientState item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        item.setUpdatedAt(new Date());
        return Single.fromPublisher(collection.insertOne(convert(item)))
                .flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> update(CimdClientState item) {
        item.setUpdatedAt(new Date());
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, item.getId()), convert(item)))
                .flatMap(result -> Single.just(item))
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

    private CimdClientState convert(CimdClientStateMongo mongo) {
        if (mongo == null) return null;
        CimdClientState state = new CimdClientState();
        state.setId(mongo.getId());
        state.setDomainId(mongo.getDomainId());
        state.setClientId(mongo.getClientId());
        state.setMonitoredPropertiesHash(mongo.getMonitoredPropertiesHash());
        state.setUpdatedAt(mongo.getUpdatedAt());
        return state;
    }

    private CimdClientStateMongo convert(CimdClientState state) {
        if (state == null) return null;
        CimdClientStateMongo mongo = new CimdClientStateMongo();
        mongo.setId(state.getId());
        mongo.setDomainId(state.getDomainId());
        mongo.setClientId(state.getClientId());
        mongo.setMonitoredPropertiesHash(state.getMonitoredPropertiesHash());
        mongo.setUpdatedAt(state.getUpdatedAt());
        return mongo;
    }
}
