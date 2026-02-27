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
import io.gravitee.am.model.PolicySet;
import io.gravitee.am.repository.management.api.PolicySetRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PolicySetMongo;
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
public class MongoPolicySetRepository extends AbstractManagementMongoRepository implements PolicySetRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<PolicySetMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("policy_sets", PolicySetMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Maybe<PolicySet> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PolicySet> findByDomain(String domainId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN_ID, domainId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PolicySet> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PolicySet> create(PolicySet item) {
        PolicySetMongo mongo = convert(item);
        mongo.setId(mongo.getId() == null ? RandomString.generate() : mongo.getId());
        return Single.fromPublisher(collection.insertOne(mongo))
                .flatMap(success -> {
                    item.setId(mongo.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PolicySet> update(PolicySet item) {
        PolicySetMongo mongo = convert(item);
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
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN_ID, domainId)))
                .observeOn(Schedulers.computation());
    }

    private PolicySet convert(PolicySetMongo mongo) {
        if (mongo == null) return null;
        PolicySet ps = new PolicySet();
        ps.setId(mongo.getId());
        ps.setDomainId(mongo.getDomainId());
        ps.setName(mongo.getName());
        ps.setLatestVersion(mongo.getLatestVersion());
        ps.setCreatedAt(mongo.getCreatedAt());
        ps.setUpdatedAt(mongo.getUpdatedAt());
        return ps;
    }

    private PolicySetMongo convert(PolicySet ps) {
        if (ps == null) return null;
        PolicySetMongo mongo = new PolicySetMongo();
        mongo.setId(ps.getId());
        mongo.setDomainId(ps.getDomainId());
        mongo.setName(ps.getName());
        mongo.setLatestVersion(ps.getLatestVersion());
        mongo.setCreatedAt(ps.getCreatedAt());
        mongo.setUpdatedAt(ps.getUpdatedAt());
        return mongo;
    }
}
