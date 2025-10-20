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
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.DeviceIdentifierRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.DeviceIdentifierMongo;
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
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoDeviceIdentifierRepository extends AbstractManagementMongoRepository implements DeviceIdentifierRepository {

    public static final String COLLECTION_NAME = "device_identifiers";
    private MongoCollection<DeviceIdentifierMongo> deviceIdentifierMongoMongoCollection;


    @PostConstruct
    public void init() {
        deviceIdentifierMongoMongoCollection = mongoOperations.getCollection(COLLECTION_NAME, DeviceIdentifierMongo.class);
        super.init(deviceIdentifierMongoMongoCollection);
        super.createIndex(deviceIdentifierMongoMongoCollection, Map.of(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1")));
    }

    @Override
    public Flowable<DeviceIdentifier> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(deviceIdentifierMongoMongoCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(Reference reference) {
        return Completable.fromPublisher(deviceIdentifierMongoMongoCollection.deleteMany(and(eq(FIELD_REFERENCE_ID, reference.id()), eq(FIELD_REFERENCE_TYPE, reference.type().name()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<DeviceIdentifier> findById(String botDetectionId) {
        return Observable.fromPublisher(deviceIdentifierMongoMongoCollection.find(eq(FIELD_ID, botDetectionId)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceIdentifier> create(DeviceIdentifier item) {
        DeviceIdentifierMongo entity = convert(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(deviceIdentifierMongoMongoCollection.insertOne(entity)).flatMap(success -> { item.setId(entity.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceIdentifier> update(DeviceIdentifier item) {
        DeviceIdentifierMongo entity = convert(item);
        return Single.fromPublisher(deviceIdentifierMongoMongoCollection.replaceOne(eq(FIELD_ID, entity.getId()), entity)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(deviceIdentifierMongoMongoCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private DeviceIdentifier convert(DeviceIdentifierMongo entity) {
        return ofNullable(entity).filter(Objects::nonNull).map(e -> {
                    var deviceIdentifier = new DeviceIdentifier();
                    deviceIdentifier.setId(e.getId());
                    deviceIdentifier.setName(e.getName());
                    deviceIdentifier.setType(e.getType());
                    deviceIdentifier.setConfiguration(e.getConfiguration());
                    deviceIdentifier.setReferenceId(e.getReferenceId());
                    deviceIdentifier.setReferenceType(ReferenceType.valueOf(e.getReferenceType()));
                    deviceIdentifier.setCreatedAt(e.getCreatedAt());
                    deviceIdentifier.setUpdatedAt(e.getUpdatedAt());
                    return deviceIdentifier;
                }
        ).orElse(null);
    }

    private DeviceIdentifierMongo convert(DeviceIdentifier bean) {
        return ofNullable(bean).filter(Objects::nonNull).map(remeberDevice -> {
            var entity = new DeviceIdentifierMongo();
            entity.setId(remeberDevice.getId());
            entity.setName(remeberDevice.getName());
            entity.setType(remeberDevice.getType());
            entity.setConfiguration(remeberDevice.getConfiguration());
            entity.setReferenceType(remeberDevice.getReferenceType().name());
            entity.setReferenceId(remeberDevice.getReferenceId());
            entity.setCreatedAt(remeberDevice.getCreatedAt());
            entity.setUpdatedAt(remeberDevice.getUpdatedAt());
            return entity;
        }).orElse(null);
    }
}
