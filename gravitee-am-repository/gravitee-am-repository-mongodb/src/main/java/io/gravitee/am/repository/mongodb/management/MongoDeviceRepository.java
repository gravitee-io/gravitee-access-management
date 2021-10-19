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
import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.DeviceRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.DeviceMongo;
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoDeviceRepository extends AbstractManagementMongoRepository implements DeviceRepository {

    private static final String COLLECTION_NAME = "devices";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    public static final String DEVICE_IDENTIFIER_ID = "deviceIdentifierId";
    public static final String DEVICE_ID = "deviceId";
    public static final String CLIENT = "client";
    private MongoCollection<DeviceMongo> rememberDeviceMongoCollection;


    @PostConstruct
    public void init() {
        rememberDeviceMongoCollection = mongoOperations.getCollection(COLLECTION_NAME, DeviceMongo.class);
        super.init(rememberDeviceMongoCollection);

        super.createIndex(rememberDeviceMongoCollection, new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1));
        super.createIndex(rememberDeviceMongoCollection, new Document(FIELD_EXPIRES_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    @Override
    public Flowable<Device> findByReferenceAndUser(ReferenceType referenceType, String referenceId, String user) {
        var query = and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_USER_ID, user));
        var devicePublisher = rememberDeviceMongoCollection.find(query);
        return Flowable.fromPublisher(devicePublisher).map(this::convert);

    }

    @Override
    public Maybe<Device> findByReferenceAndClientAndUserAndDeviceIdentifierAndDeviceId(
            ReferenceType referenceType, String referenceId, String client, String user, String deviceIdentifierId, String deviceId) {
        var query = and(
                eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                eq(FIELD_CLIENT, client), eq(FIELD_USER_ID, user), eq(DEVICE_IDENTIFIER_ID, deviceIdentifierId), eq(DEVICE_ID, deviceId));
        return Observable.fromPublisher(rememberDeviceMongoCollection.find(query).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<Device> findById(String deviceId) {
        return Observable.fromPublisher(rememberDeviceMongoCollection.find(eq(FIELD_ID, deviceId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Device> create(Device item) {
        DeviceMongo entity = convert(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(rememberDeviceMongoCollection.insertOne(entity)).flatMap(success -> findById(entity.getId()).toSingle());
    }

    @Override
    public Single<Device> update(Device item) {
        DeviceMongo entity = convert(item);
        return Single.fromPublisher(rememberDeviceMongoCollection.replaceOne(eq(FIELD_ID, entity.getId()), entity)).flatMap(updateResult -> findById(entity.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(rememberDeviceMongoCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private Device convert(DeviceMongo entity) {
        return ofNullable(entity).filter(Objects::nonNull).map(device -> new Device().setId(device.getId())
                .setType(device.getType())
                .setReferenceId(device.getReferenceId())
                .setReferenceType(ReferenceType.valueOf(device.getReferenceType()))
                .setClient(device.getClient())
                .setUserId(device.getUserId())
                .setDeviceIdentifierId(device.getDeviceIdentifierId())
                .setDeviceId(device.getDeviceId())
                .setCreatedAt(device.getCreatedAt())
                .setExpiresAt(device.getExpiresAt())
        ).orElse(null);
    }

    private DeviceMongo convert(Device bean) {
        return ofNullable(bean).filter(Objects::nonNull).map(device -> new DeviceMongo()
                .setId(device.getId())
                .setType(device.getType())
                .setReferenceType(device.getReferenceType().name())
                .setReferenceId(device.getReferenceId())
                .setClient(device.getClient())
                .setUserId(device.getUserId())
                .setDeviceIdentifierId(device.getDeviceIdentifierId())
                .setDeviceId(device.getDeviceId())
                .setCreatedAt(device.getCreatedAt())
                .setExpiresAt(device.getExpiresAt())
        ).orElse(null);
    }
}
