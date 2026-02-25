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
import io.gravitee.am.model.AuthorizationBundleVersion;
import io.gravitee.am.repository.management.api.AuthorizationBundleVersionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationBundleVersionMongo;
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
public class MongoAuthorizationBundleVersionRepository extends AbstractManagementMongoRepository implements AuthorizationBundleVersionRepository {

    private static final String FIELD_BUNDLE_ID = "bundleId";
    private static final String FIELD_DOMAIN_ID = "domainId";
    private static final String FIELD_VERSION = "version";

    private MongoCollection<AuthorizationBundleVersionMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_bundle_versions", AuthorizationBundleVersionMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_BUNDLE_ID, 1).append(FIELD_VERSION, -1), new IndexOptions().name("bi1v-1"),
                new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")
        ));
    }

    @Override
    public Flowable<AuthorizationBundleVersion> findByBundleId(String bundleId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_BUNDLE_ID, bundleId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationBundleVersion> findByBundleIdAndVersion(String bundleId, int version) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_BUNDLE_ID, bundleId), eq(FIELD_VERSION, version))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationBundleVersion> create(AuthorizationBundleVersion item) {
        AuthorizationBundleVersionMongo versionMongo = convert(item);
        versionMongo.setId(versionMongo.getId() == null ? RandomString.generate() : versionMongo.getId());
        return Single.fromPublisher(collection.insertOne(versionMongo))
                .flatMap(success -> {
                    item.setId(versionMongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByBundleId(String bundleId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_BUNDLE_ID, bundleId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN_ID, domainId)))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationBundleVersion convert(AuthorizationBundleVersionMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationBundleVersion version = new AuthorizationBundleVersion();
        version.setId(mongo.getId());
        version.setBundleId(mongo.getBundleId());
        version.setDomainId(mongo.getDomainId());
        version.setVersion(mongo.getVersion());
        version.setSchema(mongo.getSchema());
        version.setPolicies(mongo.getPolicies());
        version.setEntities(mongo.getEntities());
        version.setComment(mongo.getComment());
        version.setCreatedBy(mongo.getCreatedBy());
        version.setCreatedAt(mongo.getCreatedAt());

        return version;
    }

    private AuthorizationBundleVersionMongo convert(AuthorizationBundleVersion version) {
        if (version == null) {
            return null;
        }

        AuthorizationBundleVersionMongo mongo = new AuthorizationBundleVersionMongo();
        mongo.setId(version.getId());
        mongo.setBundleId(version.getBundleId());
        mongo.setDomainId(version.getDomainId());
        mongo.setVersion(version.getVersion());
        mongo.setSchema(version.getSchema());
        mongo.setPolicies(version.getPolicies());
        mongo.setEntities(version.getEntities());
        mongo.setComment(version.getComment());
        mongo.setCreatedBy(version.getCreatedBy());
        mongo.setCreatedAt(version.getCreatedAt());

        return mongo;
    }
}
