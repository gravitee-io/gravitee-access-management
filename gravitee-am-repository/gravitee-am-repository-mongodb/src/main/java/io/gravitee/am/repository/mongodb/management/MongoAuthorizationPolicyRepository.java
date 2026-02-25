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
import io.gravitee.am.model.AuthorizationPolicy;
import io.gravitee.am.repository.management.api.AuthorizationPolicyRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationPolicyMongo;
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
public class MongoAuthorizationPolicyRepository extends AbstractManagementMongoRepository implements AuthorizationPolicyRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";
    private static final String FIELD_ENGINE_TYPE = "engineType";

    private MongoCollection<AuthorizationPolicyMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_policies", AuthorizationPolicyMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Maybe<AuthorizationPolicy> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationPolicy> findByDomain(String domainId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN_ID, domainId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationPolicy> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationPolicy> findByDomainAndEngineType(String domainId, String engineType) {
        return Flowable.fromPublisher(withMaxTime(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ENGINE_TYPE, engineType)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationPolicy> create(AuthorizationPolicy item) {
        AuthorizationPolicyMongo policy = convert(item);
        policy.setId(policy.getId() == null ? RandomString.generate() : policy.getId());
        return Single.fromPublisher(collection.insertOne(policy))
                .flatMap(success -> {
                    item.setId(policy.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationPolicy> update(AuthorizationPolicy item) {
        AuthorizationPolicyMongo policy = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, policy.getId()), policy))
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

    private AuthorizationPolicy convert(AuthorizationPolicyMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationPolicy policy = new AuthorizationPolicy();
        policy.setId(mongo.getId());
        policy.setDomainId(mongo.getDomainId());
        policy.setName(mongo.getName());
        policy.setDescription(mongo.getDescription());
        policy.setEngineType(mongo.getEngineType());
        policy.setContent(mongo.getContent());
        policy.setVersion(mongo.getVersion());
        policy.setCreatedAt(mongo.getCreatedAt());
        policy.setUpdatedAt(mongo.getUpdatedAt());

        return policy;
    }

    private AuthorizationPolicyMongo convert(AuthorizationPolicy policy) {
        if (policy == null) {
            return null;
        }

        AuthorizationPolicyMongo mongo = new AuthorizationPolicyMongo();
        mongo.setId(policy.getId());
        mongo.setDomainId(policy.getDomainId());
        mongo.setName(policy.getName());
        mongo.setDescription(policy.getDescription());
        mongo.setEngineType(policy.getEngineType());
        mongo.setContent(policy.getContent());
        mongo.setVersion(policy.getVersion());
        mongo.setCreatedAt(policy.getCreatedAt());
        mongo.setUpdatedAt(policy.getUpdatedAt());

        return mongo;
    }
}
