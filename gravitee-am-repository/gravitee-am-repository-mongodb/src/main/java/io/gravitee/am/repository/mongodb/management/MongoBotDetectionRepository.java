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
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.BotDetectionMongo;
import io.reactivex.rxjava3.core.*;
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
public class MongoBotDetectionRepository extends AbstractManagementMongoRepository implements BotDetectionRepository {

    public static final String COLLECTION_NAME = "bot_detections";
    private MongoCollection<BotDetectionMongo> botDetectionMongoCollection;

    @PostConstruct
    public void init() {
        botDetectionMongoCollection = mongoOperations.getCollection(COLLECTION_NAME, BotDetectionMongo.class);
        super.init(botDetectionMongoCollection);
        super.createIndex(botDetectionMongoCollection, new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1"));
    }

    @Override
    public Flowable<BotDetection> findAll() {
        return Flowable.fromPublisher(botDetectionMongoCollection.find()).map(this::convert);
    }

    @Override
    public Flowable<BotDetection> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(botDetectionMongoCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))).map(this::convert);
    }

    @Override
    public Maybe<BotDetection> findById(String botDetectionId) {
        return Observable.fromPublisher(botDetectionMongoCollection.find(eq(FIELD_ID, botDetectionId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<BotDetection> create(BotDetection item) {
        BotDetectionMongo entity = convert(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return Single.fromPublisher(botDetectionMongoCollection.insertOne(entity))
                .flatMap(success -> {
                    item.setId(entity.getId());
                    return Single.just(item);
                });
    }

    @Override
    public Single<BotDetection> update(BotDetection item) {
        BotDetectionMongo entity = convert(item);
        return Single.fromPublisher(botDetectionMongoCollection.replaceOne(eq(FIELD_ID, entity.getId()), entity))
                .flatMap(updateResult -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(botDetectionMongoCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private BotDetection convert(BotDetectionMongo entity) {
        if (entity == null) {
            return null;
        }

        BotDetection bean = new BotDetection();
        bean.setId(entity.getId());
        bean.setName(entity.getName());
        bean.setType(entity.getType());
        bean.setDetectionType(entity.getDetectionType());
        bean.setConfiguration(entity.getConfiguration());
        bean.setReferenceId(entity.getReferenceId());
        bean.setReferenceType(ReferenceType.valueOf(entity.getReferenceType()));
        bean.setCreatedAt(entity.getCreatedAt());
        bean.setUpdatedAt(entity.getUpdatedAt());
        return bean;
    }

    private BotDetectionMongo convert(BotDetection bean) {
        if (bean == null) {
            return null;
        }

        BotDetectionMongo entity = new BotDetectionMongo();
        entity.setId(bean.getId());
        entity.setName(bean.getName());
        entity.setType(bean.getType());
        entity.setDetectionType(bean.getDetectionType());
        entity.setConfiguration(bean.getConfiguration());
        entity.setReferenceType(bean.getReferenceType().name());
        entity.setReferenceId(bean.getReferenceId());
        entity.setCreatedAt(bean.getCreatedAt());
        entity.setUpdatedAt(bean.getUpdatedAt());
        return entity;
    }
}
