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
import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PolicyMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.stereotype.Component;
/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoPolicyRepository extends AbstractManagementMongoRepository implements PolicyRepository {

    public static final String COLLECTION_NAME = "policies";

    @Override
    public Flowable<Policy> findAll() {
        MongoCollection<PolicyMongo> policiesCollection = mongoOperations.getCollection(COLLECTION_NAME, PolicyMongo.class);
        return Flowable.fromPublisher(withMaxTime(policiesCollection.find())).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Boolean> collectionExists() {
        return Observable.fromPublisher(mongoOperations.listCollectionNames())
                .filter(collectionName -> collectionName.equalsIgnoreCase(COLLECTION_NAME))
                .isEmpty()
                .map(isEmpty -> !isEmpty)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteCollection() {
        return Completable.fromPublisher(mongoOperations.getCollection(COLLECTION_NAME).drop())
                .observeOn(Schedulers.computation());
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
