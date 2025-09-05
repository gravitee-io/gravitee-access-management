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
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ServiceResourceMongo;
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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoServiceResourceRepository extends AbstractManagementMongoRepository implements ServiceResourceRepository {

    private MongoCollection<ServiceResourceMongo> resourceCollection;

    @PostConstruct
    public void init() {
        resourceCollection = mongoOperations.getCollection("service_resources", ServiceResourceMongo.class);
        super.init(resourceCollection);
        super.createIndex(resourceCollection, Map.of(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1")));
    }

    @Override
    public Flowable<ServiceResource> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(resourceCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<ServiceResource> findById(String id) {
        return Observable.fromPublisher(resourceCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ServiceResource> create(ServiceResource item) {
        ServiceResourceMongo res = convert(item);
        res.setId(res.getId() == null ? RandomString.generate() : res.getId());
        return Single.fromPublisher(resourceCollection.insertOne(res)).flatMap(success -> { item.setId(res.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ServiceResource> update(ServiceResource item) {
        ServiceResourceMongo authenticator = convert(item);
        return Single.fromPublisher(resourceCollection.replaceOne(eq(FIELD_ID, authenticator.getId()), authenticator)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(resourceCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private ServiceResource convert(ServiceResourceMongo resMongo) {
        if (resMongo == null) {
            return null;
        }

        ServiceResource res = new ServiceResource();
        res.setId(resMongo.getId());
        res.setName(resMongo.getName());
        res.setType(resMongo.getType());
        res.setConfiguration(resMongo.getConfiguration());
        res.setReferenceId(resMongo.getReferenceId());
        res.setReferenceType(ReferenceType.valueOf(resMongo.getReferenceType()));
        res.setCreatedAt(resMongo.getCreatedAt());
        res.setUpdatedAt(resMongo.getUpdatedAt());
        return res;
    }

    private ServiceResourceMongo convert(ServiceResource res) {
        if (res == null) {
            return null;
        }

        ServiceResourceMongo resMongo = new ServiceResourceMongo();
        resMongo.setId(res.getId());
        resMongo.setName(res.getName());
        resMongo.setType(res.getType());
        resMongo.setConfiguration(res.getConfiguration());
        resMongo.setReferenceId(res.getReferenceId());
        resMongo.setReferenceType(res.getReferenceType().name());
        resMongo.setCreatedAt(res.getCreatedAt());
        resMongo.setUpdatedAt(res.getUpdatedAt());
        return resMongo;
    }
}
