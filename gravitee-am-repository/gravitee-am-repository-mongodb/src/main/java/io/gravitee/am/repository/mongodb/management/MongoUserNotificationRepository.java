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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.gravitee.am.repository.management.api.UserNotificationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.UserNotificationMongo;
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.Date;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserNotificationRepository extends AbstractManagementMongoRepository implements UserNotificationRepository {

    public static final String COLLECTION_NAME = "user_notifications";

    public static final String FIELD_AUDIENCE = "audienceId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_TYPE = "type";
    public static final int NOTIFICATION_LIMIT = 20;
    public static final String FIELD_CREATED_AT = "created_at";

    private MongoCollection<UserNotificationMongo> mongoCollection;

    @PostConstruct
    protected void init() {
        this.mongoCollection = mongoOperations.getCollection(COLLECTION_NAME, UserNotificationMongo.class);
        super.init(mongoCollection);
        createIndex(this.mongoCollection, new Document(FIELD_AUDIENCE, 1).append(FIELD_TYPE, 1).append(FIELD_STATUS, 1));
    }

    @Override
    public Maybe<UserNotification> findById(String id) {
        return Observable.fromPublisher(mongoCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<UserNotification> create(UserNotification item) {
        UserNotificationMongo entity = convert(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(mongoCollection.insertOne(entity)).flatMap(success -> findById(entity.getId()).toSingle());
    }

    @Override
    public Single<UserNotification> update(UserNotification item) {
        UserNotificationMongo entity = convert(item);
        return Single.fromPublisher(mongoCollection.replaceOne(eq(FIELD_ID, entity.getId()), entity)).flatMap(updateResult -> findById(entity.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(mongoCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Flowable<UserNotification> findAllByAudienceAndStatus(String audience, UserNotificationStatus status) {
        return Flowable.fromPublisher(mongoCollection.find(and(
                                eq(FIELD_AUDIENCE, audience),
                                eq(FIELD_STATUS, status.name())))
                        .limit(NOTIFICATION_LIMIT)
                        .sort(new Document(FIELD_CREATED_AT, 1))
                )
                .map(this::convert);
    }

    @Override
    public Completable updateNotificationStatus(String id, UserNotificationStatus status) {
        Document statusDocument = new Document();
        statusDocument.put(FIELD_STATUS, status.name());
        statusDocument.put(FIELD_UPDATED_AT, new Date());

        Document updateObject = new Document();
        updateObject.put("$set", statusDocument);

        return Completable.fromPublisher(mongoCollection.updateOne(eq(FIELD_ID, id), updateObject));
    }

    private UserNotification convert(UserNotificationMongo entity) {
        if (entity == null) {
            return null;
        }

        UserNotification bean = new UserNotification();
        bean.setId(entity.getId());
        bean.setAudienceId(entity.getAudienceId());
        bean.setMessage(entity.getMessage());
        bean.setStatus(entity.getStatus());
        bean.setReferenceId(entity.getReferenceId());
        bean.setReferenceType(entity.getReferenceType());
        bean.setCreatedAt(entity.getCreatedAt());
        bean.setUpdatedAt(entity.getUpdatedAt());
        return bean;
    }

    private UserNotificationMongo convert(UserNotification bean) {
        if (bean == null) {
            return null;
        }

        UserNotificationMongo entity = new UserNotificationMongo();
        entity.setId(bean.getId());
        entity.setAudienceId(bean.getAudienceId());
        entity.setMessage(bean.getMessage());
        entity.setStatus(bean.getStatus());
        entity.setReferenceType(bean.getReferenceType());
        entity.setReferenceId(bean.getReferenceId());
        entity.setCreatedAt(bean.getCreatedAt());
        entity.setUpdatedAt(bean.getUpdatedAt());

        return entity;
    }
}
