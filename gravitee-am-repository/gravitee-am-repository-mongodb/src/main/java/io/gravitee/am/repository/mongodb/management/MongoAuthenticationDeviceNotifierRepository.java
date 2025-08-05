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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthenticationDeviceNotifierMongo;
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
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthenticationDeviceNotifierRepository extends AbstractManagementMongoRepository implements AuthenticationDeviceNotifierRepository {

    public static final String COLLECTION_NAME = "authentication_device_notifiers";
    private MongoCollection<AuthenticationDeviceNotifierMongo> authDeviceNotifierCollection;

    @PostConstruct
    public void init() {
        authDeviceNotifierCollection = mongoOperations.getCollection(COLLECTION_NAME, AuthenticationDeviceNotifierMongo.class);
        super.init(authDeviceNotifierCollection);
        super.createIndex(authDeviceNotifierCollection, Map.of(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1")));
    }

    @Override
    public Flowable<AuthenticationDeviceNotifier> findAll() {
        return Flowable.fromPublisher(withMaxTime(authDeviceNotifierCollection.find())).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthenticationDeviceNotifier> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(withMaxTime(authDeviceNotifierCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()))))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthenticationDeviceNotifier> findById(String botDetectionId) {
        return Observable.fromPublisher(authDeviceNotifierCollection.find(eq(FIELD_ID, botDetectionId)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthenticationDeviceNotifier> create(AuthenticationDeviceNotifier item) {
        AuthenticationDeviceNotifierMongo entity = convert(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(authDeviceNotifierCollection.insertOne(entity)).flatMap(success -> findById(entity.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthenticationDeviceNotifier> update(AuthenticationDeviceNotifier item) {
        AuthenticationDeviceNotifierMongo entity = convert(item);
        return Single.fromPublisher(authDeviceNotifierCollection.replaceOne(eq(FIELD_ID, entity.getId()), entity)).flatMap(updateResult -> findById(entity.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(authDeviceNotifierCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private AuthenticationDeviceNotifier convert(AuthenticationDeviceNotifierMongo entity) {
        if (entity == null) {
            return null;
        }

        AuthenticationDeviceNotifier bean = new AuthenticationDeviceNotifier();
        bean.setId(entity.getId());
        bean.setName(entity.getName());
        bean.setType(entity.getType());
        bean.setConfiguration(entity.getConfiguration());
        bean.setReferenceId(entity.getReferenceId());
        bean.setReferenceType(ReferenceType.valueOf(entity.getReferenceType()));
        bean.setCreatedAt(entity.getCreatedAt());
        bean.setUpdatedAt(entity.getUpdatedAt());

        return bean;
    }

    private AuthenticationDeviceNotifierMongo convert(AuthenticationDeviceNotifier bean) {
        if (bean == null) {
            return null;
        }

        AuthenticationDeviceNotifierMongo entity = new AuthenticationDeviceNotifierMongo();
        entity.setId(bean.getId());
        entity.setName(bean.getName());
        entity.setType(bean.getType());
        entity.setConfiguration(bean.getConfiguration());
        entity.setReferenceType(bean.getReferenceType().name());
        entity.setReferenceId(bean.getReferenceId());
        entity.setCreatedAt(bean.getCreatedAt());
        entity.setUpdatedAt(bean.getUpdatedAt());

        return entity;
    }
}
