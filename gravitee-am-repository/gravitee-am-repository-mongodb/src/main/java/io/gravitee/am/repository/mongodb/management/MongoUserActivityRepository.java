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
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.management.api.UserActivityRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.UserActivityMongo;
import io.reactivex.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.model.UserActivity.Type.valueOf;
import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class MongoUserActivityRepository extends AbstractManagementMongoRepository implements UserActivityRepository {

    private static final String FIELD_USER_ACTIVITY_KEY = "userActivityKey";
    private static final String FIELD_USER_ACTIVITY_TYPE = "userActivityType";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_EXPIRE_AT = "expireAt";

    private MongoCollection<UserActivityMongo> userActivityCollection;

    @PostConstruct
    public void init() {
        userActivityCollection = mongoOperations.getCollection("user_activities", UserActivityMongo.class);
        super.init(userActivityCollection);
        super.createIndex(userActivityCollection, new Document(FIELD_ID, 1));
        super.createIndex(userActivityCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(userActivityCollection, new Document(FIELD_REFERENCE_TYPE, 1)
                .append(FIELD_REFERENCE_ID, 1)
                .append(FIELD_USER_ACTIVITY_TYPE, 1)
                .append(FIELD_USER_ACTIVITY_KEY, 1));
        super.createIndex(userActivityCollection, new Document(FIELD_CREATED_AT, 1));
        super.createIndex(userActivityCollection, new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    @Override
    public Maybe<UserActivity> findById(String id) {
        return Observable.fromPublisher(userActivityCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Flowable<UserActivity> findByReferenceAndTypeAndKeyAndLimit(ReferenceType referenceType, String referenceId, Type type, String key, int limit) {
        return Flowable.fromPublisher(userActivityCollection.find(and(
                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                eq(FIELD_REFERENCE_ID, referenceId),
                eq(FIELD_USER_ACTIVITY_TYPE, type.name()),
                eq(FIELD_USER_ACTIVITY_KEY, key))
        ).sort(new Document(FIELD_CREATED_AT, -1)).limit(limit)).map(this::convert);
    }

    @Override
    public Single<UserActivity> create(UserActivity item) {
        var userActivity = convert(item);
        userActivity.setId(userActivity.getId() == null ? RandomString.generate() : userActivity.getId());
        return Single.fromPublisher(userActivityCollection.insertOne(userActivity)).flatMap(success -> {
            item.setId(userActivity.getId());
            return Single.just(item);
        });
    }

    @Override
    public Single<UserActivity> update(UserActivity item) {
        var userActivity = convert(item);
        return Single.fromPublisher(userActivityCollection.replaceOne(eq(FIELD_ID, userActivity.getId()), userActivity))
                .flatMap(updateResult -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(userActivityCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Completable deleteByReferenceAndKey(ReferenceType referenceType, String referenceId, String key) {
        return Completable.fromPublisher(userActivityCollection.deleteMany(and(
                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                eq(FIELD_REFERENCE_ID, referenceId),
                eq(FIELD_USER_ACTIVITY_KEY, key))));
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(userActivityCollection.deleteMany(and(
                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                eq(FIELD_REFERENCE_ID, referenceId))));
    }

    private UserActivity convert(UserActivityMongo userActivityMongo) {
        final UserActivity userActivity = new UserActivity();
        if (isNull(userActivityMongo)) {
            return userActivity;
        }
        return userActivity
                .setId(userActivityMongo.getId())
                .setReferenceType(ReferenceType.valueOf(userActivityMongo.getReferenceType()))
                .setReferenceId(userActivityMongo.getReferenceId())
                .setUserActivityKey(userActivityMongo.getUserActivityKey())
                .setUserActivityType(valueOf(userActivityMongo.getUserActivityType()))
                .setLatitude(userActivityMongo.getLatitude())
                .setLongitude(userActivityMongo.getLongitude())
                .setUserAgent(userActivityMongo.getUserAgent())
                .setLoginAttempts(userActivityMongo.getLoginAttempts())
                .setCreatedAt(userActivityMongo.getCreatedAt())
                .setExpireAt(userActivityMongo.getExpireAt());
    }

    private UserActivityMongo convert(UserActivity userActivity) {
        final UserActivityMongo userActivityMongo = new UserActivityMongo();
        if (isNull(userActivity)) {
            return userActivityMongo;
        }
        return userActivityMongo
                .setId(userActivity.getId())
                .setReferenceType(userActivity.getReferenceType().name())
                .setReferenceId(userActivity.getReferenceId())
                .setUserActivityType(userActivity.getUserActivityType().name())
                .setUserActivityKey(userActivity.getUserActivityKey())
                .setLatitude(userActivity.getLatitude())
                .setLongitude(userActivity.getLongitude())
                .setUserAgent(userActivity.getUserAgent())
                .setLoginAttempts(userActivity.getLoginAttempts())
                .setCreatedAt(userActivity.getCreatedAt())
                .setExpireAt(userActivity.getExpireAt());
    }
}
