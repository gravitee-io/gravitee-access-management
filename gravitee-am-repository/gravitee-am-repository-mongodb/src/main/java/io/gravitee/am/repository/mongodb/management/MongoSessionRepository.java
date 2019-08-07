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

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.Session;
import io.gravitee.am.repository.management.api.SessionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.SessionMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoSessionRepository extends AbstractManagementMongoRepository implements SessionRepository {

    private MongoCollection<SessionMongo> sessionCollection;

    private static final String FIELD_ID = "_id";
    private static final String FIELD_RESET_TIME = "expire_at";

    @PostConstruct
    public void init() {
        sessionCollection = mongoOperations.getCollection("sessions", SessionMongo.class);
        // expire after index
        super.createIndex(sessionCollection, new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    @Override
    public Maybe<Session> findById(String id) {
        return Observable
                .fromPublisher(sessionCollection.find(eq(FIELD_ID, id)).limit(1).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<Session> create(Session item) {
        return Single
                .fromPublisher(sessionCollection.insertOne(convert(item)))
                .flatMap(success -> findById(item.getId()).toSingle());
    }

    @Override
    public Single<Session> update(Session item) {
        return Single
                .fromPublisher(sessionCollection.replaceOne(eq(FIELD_ID, item.getId()), convert(item), new ReplaceOptions().upsert(true)))
                .flatMap(updateResult -> findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(sessionCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Completable clear() {
        return Completable.fromPublisher(sessionCollection.deleteMany(new BasicDBObject()));
    }

    @Override
    public Single<Long> count() {
        return Single.fromPublisher(sessionCollection.countDocuments());
    }

    private SessionMongo convert(Session session) {
        if (session == null) {
            return null;
        }

        SessionMongo sessionMongo = new SessionMongo();
        sessionMongo.setId(session.getId());
        if (session.getValue() != null) {
            sessionMongo.setValue(new Binary(session.getValue()));
        }
        sessionMongo.setCreatedAt(session.getCreatedAt());
        sessionMongo.setUpdatedAt(session.getCreatedAt());
        sessionMongo.setExpireAt(session.getExpireAt());

        return sessionMongo;
    }

    private Session convert(SessionMongo sessionMongo) {
        if (sessionMongo == null) {
            return null;
        }

        Session session = new Session();
        session.setId(sessionMongo.getId());
        if (sessionMongo.getValue() != null) {
            session.setValue(sessionMongo.getValue().getData());
        }
        session.setCreatedAt(sessionMongo.getCreatedAt());
        session.setUpdatedAt(sessionMongo.getCreatedAt());
        session.setExpireAt(sessionMongo.getExpireAt());

        return session;
    }
}
