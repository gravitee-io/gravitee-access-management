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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.LoginAttemptRepository;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.dataplane.mongodb.repository.model.LoginAttemptMongo;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_CLIENT;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoLoginAttemptRepository extends AbstractDataPlaneMongoRepository implements LoginAttemptRepository {

    private static final String FIELD_IDP = "identityProvider";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EXPIRE_AT = "expireAt";
    private MongoCollection<LoginAttemptMongo> loginAttemptsCollection;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        loginAttemptsCollection = mongoDatabase.getCollection("login_attempts", LoginAttemptMongo.class);
        MongoUtils.init(loginAttemptsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT, 1).append(FIELD_USERNAME, 1), new IndexOptions().name("d1c1u1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));

        super.createIndex(loginAttemptsCollection, indexes);
    }

    @Override
    public Maybe<LoginAttempt> findById(String id) {
        return Observable.fromPublisher(loginAttemptsCollection.find(and(eq(FIELD_ID, id), gte(FIELD_EXPIRE_AT, new Date()))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<LoginAttempt> findByCriteria(LoginAttemptCriteria criteria) {
        return Observable.fromPublisher(withMaxTime(loginAttemptsCollection.find(and(query(criteria), gte(FIELD_EXPIRE_AT, new Date())))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<LoginAttempt> create(LoginAttempt item) {
        LoginAttemptMongo loginAttempt = convert(item);
        loginAttempt.setId(loginAttempt.getId() == null ? RandomString.generate() : loginAttempt.getId());
        return Single.fromPublisher(loginAttemptsCollection.insertOne(loginAttempt)).flatMap(success -> {
            item.setId(loginAttempt.getId());
            return Single.just(item);
        })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<LoginAttempt> update(LoginAttempt item) {
        LoginAttemptMongo loginAttempt = convert(item);
        return Single.fromPublisher(loginAttemptsCollection.replaceOne(eq(FIELD_ID, loginAttempt.getId()), loginAttempt)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(loginAttemptsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(LoginAttemptCriteria criteria) {
        return Completable.fromPublisher(loginAttemptsCollection.deleteOne(query(criteria)))
                .observeOn(Schedulers.computation());
    }

    private Bson query(LoginAttemptCriteria criteria) {
        List<Bson> filters = new ArrayList<>();
        // domain
        if (criteria.domain() != null && !criteria.domain().isEmpty()) {
            filters.add(eq(FIELD_DOMAIN, criteria.domain()));
        }
        // client
        if (criteria.client() != null && !criteria.client().isEmpty()) {
            filters.add(eq(FIELD_CLIENT, criteria.client()));
        }
        // idp
        if (criteria.identityProvider() != null && !criteria.identityProvider().isEmpty()) {
            filters.add(eq(FIELD_IDP, criteria.identityProvider()));
        }
        // username
        if (criteria.username() != null && !criteria.username().isEmpty()) {
            filters.add(eq(FIELD_USERNAME, criteria.username()));
        }
        // build query
        Bson query = (filters.isEmpty()) ? new BasicDBObject() : and(filters);
        return query;
    }

    private LoginAttempt convert(LoginAttemptMongo loginAttemptMongo) {
        if (loginAttemptMongo == null) {
            return null;
        }
        LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setId(loginAttemptMongo.getId());
        loginAttempt.setDomain(loginAttemptMongo.getDomain());
        loginAttempt.setClient(loginAttemptMongo.getClient());
        loginAttempt.setIdentityProvider(loginAttemptMongo.getIdentityProvider());
        loginAttempt.setUsername(loginAttemptMongo.getUsername());
        loginAttempt.setAttempts(loginAttemptMongo.getAttempts());
        loginAttempt.setExpireAt(loginAttemptMongo.getExpireAt());
        loginAttempt.setCreatedAt(loginAttemptMongo.getCreatedAt());
        loginAttempt.setUpdatedAt(loginAttemptMongo.getUpdatedAt());
        return loginAttempt;
    }

    private LoginAttemptMongo convert(LoginAttempt loginAttempt) {
        if (loginAttempt == null) {
            return null;
        }
        LoginAttemptMongo loginAttemptMongo = new LoginAttemptMongo();
        loginAttemptMongo.setId(loginAttempt.getId());
        loginAttemptMongo.setDomain(loginAttempt.getDomain());
        loginAttemptMongo.setClient(loginAttempt.getClient());
        loginAttemptMongo.setIdentityProvider(loginAttempt.getIdentityProvider());
        loginAttemptMongo.setUsername(loginAttempt.getUsername());
        loginAttemptMongo.setAttempts(loginAttempt.getAttempts());
        loginAttemptMongo.setExpireAt(loginAttempt.getExpireAt());
        loginAttemptMongo.setCreatedAt(loginAttempt.getCreatedAt());
        loginAttemptMongo.setUpdatedAt(loginAttempt.getUpdatedAt());
        return loginAttemptMongo;
    }
}
