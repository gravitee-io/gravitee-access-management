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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.PolicyMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoPolicyRepository extends AbstractManagementMongoRepository implements PolicyRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private MongoCollection<PolicyMongo> policiesCollection;

    @PostConstruct
    public void init() {
        policiesCollection = mongoOperations.getCollection("policies", PolicyMongo.class);
        policiesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Single<List<Policy>> findAll() {
        return Observable.fromPublisher(policiesCollection.find()).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Single<List<Policy>> findByDomain(String domain) {
        return Observable.fromPublisher(policiesCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<Policy> findById(String id) {
        return Observable.fromPublisher(policiesCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Policy> create(Policy item) {
        PolicyMongo policy = convert(item);
        policy.setId(policy.getId() == null ? RandomString.generate() : policy.getId());
        return Single.fromPublisher(policiesCollection.insertOne(policy)).flatMap(success -> findById(policy.getId()).toSingle());
    }

    @Override
    public Single<Policy> update(Policy item) {
        PolicyMongo policy = convert(item);
        return Single.fromPublisher(policiesCollection.replaceOne(eq(FIELD_ID, policy.getId()), policy)).flatMap(updateResult -> findById(policy.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(policiesCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private PolicyMongo convert(Policy policy) {
        if (policy == null) {
            return null;
        }

        PolicyMongo policyMongo = new PolicyMongo();
        policyMongo.setId(policy.getId());
        policyMongo.setEnabled(policy.isEnabled());
        policyMongo.setName(policy.getName());
        policyMongo.setType(policy.getType());
        policyMongo.setExtensionPoint(policy.getExtensionPoint().toString());
        policyMongo.setOrder(policy.getOrder());
        policyMongo.setConfiguration(policy.getConfiguration());
        policyMongo.setDomain(policy.getDomain());
        policyMongo.setClient(policy.getClient());
        policyMongo.setCreatedAt(policy.getCreatedAt());
        policyMongo.setUpdatedAt(policy.getUpdatedAt());
        return policyMongo;
    }

    private Policy convert(PolicyMongo policyMongo) {
        if (policyMongo == null) {
            return null;
        }

        Policy policy = new Policy();
        policy.setId(policyMongo.getId());
        policy.setEnabled(policyMongo.isEnabled());
        policy.setName(policyMongo.getName());
        policy.setType(policyMongo.getType());
        policy.setExtensionPoint(ExtensionPoint.valueOf(policyMongo.getExtensionPoint()));
        policy.setOrder(policyMongo.getOrder());
        policy.setConfiguration(policyMongo.getConfiguration());
        policy.setDomain(policyMongo.getDomain());
        policy.setClient(policyMongo.getClient());
        policy.setCreatedAt(policyMongo.getCreatedAt());
        policy.setUpdatedAt(policyMongo.getUpdatedAt());
        return policy;
    }

}
