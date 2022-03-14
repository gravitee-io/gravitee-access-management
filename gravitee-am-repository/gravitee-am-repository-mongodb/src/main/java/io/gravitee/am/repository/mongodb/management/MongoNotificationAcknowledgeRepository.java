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
import io.gravitee.am.repository.mongodb.management.internal.model.NotificationAcknowledgeMongo;
import io.gravitee.node.api.notifier.NotificationAcknowledge;
import io.gravitee.node.api.notifier.NotificationAcknowledgeRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoNotificationAcknowledgeRepository extends AbstractManagementMongoRepository implements NotificationAcknowledgeRepository {

    private static final String FIELD_RESOURCE_ID = "resourceId";
    private static final String FIELD_RESOURCE_TYPE = "resourceType";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_AUDIENCE_ID = "audienceId";

    private MongoCollection<NotificationAcknowledgeMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("notification_acknowledgements", NotificationAcknowledgeMongo.class);
        super.createIndex(collection, new Document(FIELD_RESOURCE_ID, 1).append(FIELD_TYPE, 1).append(FIELD_AUDIENCE_ID, 1));
        super.init(collection);
    }

    @Override
    public Maybe<NotificationAcknowledge> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id))
                        .first())
                .firstElement()
                .map(this::convert);

    }

    @Override
    public Maybe<NotificationAcknowledge> findByResourceIdAndTypeAndAudienceId(String resourceId, String resourceType, String type, String audience) {
        return Observable.fromPublisher(collection.find(and(
                    eq(FIELD_RESOURCE_ID, resourceId),
                    eq(FIELD_RESOURCE_TYPE, resourceType),
                    eq(FIELD_TYPE, type),
                    eq(FIELD_AUDIENCE_ID, audience)))
                .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Completable deleteByResourceId(String id, String resourceType) {
        return Completable.fromPublisher(collection.deleteOne(and(eq(FIELD_RESOURCE_ID, id), eq(FIELD_RESOURCE_TYPE, resourceType))));
    }

    @Override
    public Single<NotificationAcknowledge> create(NotificationAcknowledge notificationAcknowledge) {
        NotificationAcknowledgeMongo entity = convert(notificationAcknowledge);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(collection.insertOne(entity)).map(success -> {
            notificationAcknowledge.setId(entity.getId());
            return notificationAcknowledge;
        });
    }

    @Override
    public Single<NotificationAcknowledge> update(NotificationAcknowledge notificationAcknowledge) {
        NotificationAcknowledgeMongo entity = convert(notificationAcknowledge);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, entity.getId()), entity))
                .map(result -> notificationAcknowledge);
    }

    private NotificationAcknowledge convert(NotificationAcknowledgeMongo entity) {
        final NotificationAcknowledge bean = new NotificationAcknowledge();
        bean.setId(entity.getId());
        bean.setResourceId(entity.getResourceId());
        bean.setResourceType(entity.getResourceType());
        bean.setAudienceId(entity.getAudienceId());
        bean.setType(entity.getType());
        bean.setCreatedAt(entity.getCreatedAt());
        bean.setUpdatedAt(entity.getUpdatedAt());
        bean.setCounter(entity.getCounter());
        return bean;
    }

    private NotificationAcknowledgeMongo convert(NotificationAcknowledge bean) {
        final NotificationAcknowledgeMongo entity = new NotificationAcknowledgeMongo();
        entity.setId(bean.getId());
        entity.setResourceId(bean.getResourceId());
        entity.setResourceType(bean.getResourceType());
        entity.setAudienceId(bean.getAudienceId());
        entity.setCounter(bean.getCounter());
        entity.setType(bean.getType());
        entity.setCreatedAt(bean.getCreatedAt());
        entity.setUpdatedAt(bean.getUpdatedAt());
        return entity;
    }
}