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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.DeviceAuthorizationRequestMongo;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoDeviceAuthorizationRequestRepository extends AbstractOAuth2MongoRepository implements DeviceAuthorizationRequestRepository {

    private static final String COLLECTION_NAME = "device_authorization_requests";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_USER_CODE = "user_code";
    private static final String FIELD_STATUS = "status";

    private MongoCollection<DeviceAuthorizationRequestMongo> deviceAuthorizationRequestCollection;

    @PostConstruct
    public void init() {
        deviceAuthorizationRequestCollection = mongoOperations.getCollection(COLLECTION_NAME, DeviceAuthorizationRequestMongo.class);
        super.init(deviceAuthorizationRequestCollection);

        final Map<Document, IndexOptions> indexes = new LinkedHashMap<>();
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS).name("e1"));
        indexes.put(new Document(FIELD_USER_CODE, 1), new IndexOptions().name("uc1").unique(true));
        super.createIndex(deviceAuthorizationRequestCollection, indexes);
    }

    @Override
    public Maybe<DeviceAuthorizationRequest> findById(String deviceCode) {
        return Observable
                .fromPublisher(deviceAuthorizationRequestCollection.find(and(eq(FIELD_ID, deviceCode), gt(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<DeviceAuthorizationRequest> findByUserCode(String userCode) {
        return Observable
                .fromPublisher(deviceAuthorizationRequestCollection.find(and(eq(FIELD_USER_CODE, userCode), gt(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> create(DeviceAuthorizationRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        final DeviceAuthorizationRequestMongo stored = convert(request);
        return Single
                .fromPublisher(deviceAuthorizationRequestCollection.insertOne(stored))
                .map(success -> convert(stored))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> update(DeviceAuthorizationRequest request) {
        final DeviceAuthorizationRequestMongo stored = convert(request);
        return Single
                .fromPublisher(deviceAuthorizationRequestCollection.replaceOne(eq(FIELD_ID, request.getId()), stored))
                .map(success -> convert(stored))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> updateStatus(String deviceCode, String status) {
        return Single
                .fromPublisher(deviceAuthorizationRequestCollection.updateOne(eq(FIELD_ID, deviceCode), Updates.set(FIELD_STATUS, status)))
                .flatMap(updateResult -> findById(deviceCode).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String deviceCode) {
        return Completable.fromPublisher(deviceAuthorizationRequestCollection.findOneAndDelete(eq(FIELD_ID, deviceCode)))
                .observeOn(Schedulers.computation());
    }

    private DeviceAuthorizationRequestMongo convert(DeviceAuthorizationRequest request) {
        if (request == null) {
            return null;
        }

        DeviceAuthorizationRequestMongo requestMongo = new DeviceAuthorizationRequestMongo();
        requestMongo.setId(request.getId());
        requestMongo.setUserCode(request.getUserCode());
        requestMongo.setStatus(request.getStatus());
        requestMongo.setClient(request.getClientId());
        requestMongo.setSubject(request.getSubject());
        requestMongo.setScopes(request.getScopes());
        requestMongo.setCreatedAt(request.getCreatedAt());
        requestMongo.setLastAccessAt(request.getLastAccessAt());
        requestMongo.setExpireAt(request.getExpireAt());
        requestMongo.setIntervalIncrement(request.getIntervalIncrement());
        return requestMongo;
    }

    private DeviceAuthorizationRequest convert(DeviceAuthorizationRequestMongo requestMongo) {
        if (requestMongo == null) {
            return null;
        }

        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId(requestMongo.getId());
        request.setUserCode(requestMongo.getUserCode());
        request.setStatus(requestMongo.getStatus());
        request.setClientId(requestMongo.getClient());
        request.setSubject(requestMongo.getSubject());
        request.setScopes(requestMongo.getScopes());
        request.setCreatedAt(requestMongo.getCreatedAt());
        request.setLastAccessAt(requestMongo.getLastAccessAt());
        request.setExpireAt(requestMongo.getExpireAt());
        request.setIntervalIncrement(requestMongo.getIntervalIncrement());
        return request;
    }
}
