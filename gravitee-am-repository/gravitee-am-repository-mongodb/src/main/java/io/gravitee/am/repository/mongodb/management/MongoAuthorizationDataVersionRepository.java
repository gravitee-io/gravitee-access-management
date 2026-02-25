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
import io.gravitee.am.model.AuthorizationDataVersion;
import io.gravitee.am.repository.management.api.AuthorizationDataVersionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationDataVersionMongo;
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
public class MongoAuthorizationDataVersionRepository extends AbstractManagementMongoRepository implements AuthorizationDataVersionRepository {

    private static final String FIELD_DATA_ID = "dataId";
    private static final String FIELD_DOMAIN_ID = "domainId";
    private static final String FIELD_VERSION = "version";

    private MongoCollection<AuthorizationDataVersionMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_data_versions", AuthorizationDataVersionMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_DATA_ID, 1).append(FIELD_VERSION, -1), new IndexOptions().name("di1v-1"),
                new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")
        ));
    }

    @Override
    public Flowable<AuthorizationDataVersion> findByDataId(String dataId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DATA_ID, dataId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDataVersion> findByDataIdAndVersion(String dataId, int version) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DATA_ID, dataId), eq(FIELD_VERSION, version))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDataVersion> create(AuthorizationDataVersion item) {
        AuthorizationDataVersionMongo versionMongo = convert(item);
        versionMongo.setId(versionMongo.getId() == null ? RandomString.generate() : versionMongo.getId());
        return Single.fromPublisher(collection.insertOne(versionMongo))
                .flatMap(success -> {
                    item.setId(versionMongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDataId(String dataId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DATA_ID, dataId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN_ID, domainId)))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationDataVersion convert(AuthorizationDataVersionMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationDataVersion version = new AuthorizationDataVersion();
        version.setId(mongo.getId());
        version.setDataId(mongo.getDataId());
        version.setDomainId(mongo.getDomainId());
        version.setVersion(mongo.getVersion());
        version.setContent(mongo.getContent());
        version.setComment(mongo.getComment());
        version.setCreatedBy(mongo.getCreatedBy());
        version.setCreatedAt(mongo.getCreatedAt());

        return version;
    }

    private AuthorizationDataVersionMongo convert(AuthorizationDataVersion version) {
        if (version == null) {
            return null;
        }

        AuthorizationDataVersionMongo mongo = new AuthorizationDataVersionMongo();
        mongo.setId(version.getId());
        mongo.setDataId(version.getDataId());
        mongo.setDomainId(version.getDomainId());
        mongo.setVersion(version.getVersion());
        mongo.setContent(version.getContent());
        mongo.setComment(version.getComment());
        mongo.setCreatedBy(version.getCreatedBy());
        mongo.setCreatedAt(version.getCreatedAt());

        return mongo;
    }
}
