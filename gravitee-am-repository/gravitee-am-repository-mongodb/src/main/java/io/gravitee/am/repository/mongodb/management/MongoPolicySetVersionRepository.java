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
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.PolicySetVersion;
import io.gravitee.am.repository.management.api.PolicySetVersionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PolicySetVersionMongo;
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
public class MongoPolicySetVersionRepository extends AbstractManagementMongoRepository implements PolicySetVersionRepository {

    private static final String FIELD_POLICY_SET_ID = "policySetId";
    private static final String FIELD_VERSION = "version";

    private MongoCollection<PolicySetVersionMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("policy_set_versions", PolicySetVersionMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_POLICY_SET_ID, 1).append(FIELD_VERSION, -1),
                new IndexOptions().name("psv1").unique(true)
        ));
    }

    @Override
    public Maybe<PolicySetVersion> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PolicySetVersion> findByPolicySetId(String policySetId) {
        return Flowable.fromPublisher(
                        withMaxTime(collection.find(eq(FIELD_POLICY_SET_ID, policySetId))
                                .sort(Sorts.descending(FIELD_VERSION))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PolicySetVersion> findByPolicySetIdAndVersion(String policySetId, int version) {
        return Observable.fromPublisher(
                        collection.find(and(eq(FIELD_POLICY_SET_ID, policySetId), eq(FIELD_VERSION, version))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PolicySetVersion> findLatestByPolicySetId(String policySetId) {
        return Observable.fromPublisher(
                        collection.find(eq(FIELD_POLICY_SET_ID, policySetId))
                                .sort(Sorts.descending(FIELD_VERSION))
                                .first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PolicySetVersion> create(PolicySetVersion item) {
        PolicySetVersionMongo mongo = convert(item);
        mongo.setId(mongo.getId() == null ? RandomString.generate() : mongo.getId());
        return Single.fromPublisher(collection.insertOne(mongo))
                .flatMap(success -> {
                    item.setId(mongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PolicySetVersion> update(PolicySetVersion item) {
        PolicySetVersionMongo mongo = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, mongo.getId()), mongo))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByPolicySetId(String policySetId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_POLICY_SET_ID, policySetId)))
                .observeOn(Schedulers.computation());
    }

    private PolicySetVersion convert(PolicySetVersionMongo mongo) {
        if (mongo == null) return null;
        PolicySetVersion v = new PolicySetVersion();
        v.setId(mongo.getId());
        v.setPolicySetId(mongo.getPolicySetId());
        v.setVersion(mongo.getVersion());
        v.setContent(mongo.getContent());
        v.setCommitMessage(mongo.getCommitMessage());
        v.setCreatedAt(mongo.getCreatedAt());
        v.setCreatedBy(mongo.getCreatedBy());
        return v;
    }

    private PolicySetVersionMongo convert(PolicySetVersion v) {
        if (v == null) return null;
        PolicySetVersionMongo mongo = new PolicySetVersionMongo();
        mongo.setId(v.getId());
        mongo.setPolicySetId(v.getPolicySetId());
        mongo.setVersion(v.getVersion());
        mongo.setContent(v.getContent());
        mongo.setCommitMessage(v.getCommitMessage());
        mongo.setCreatedAt(v.getCreatedAt());
        mongo.setCreatedBy(v.getCreatedBy());
        return mongo;
    }
}
