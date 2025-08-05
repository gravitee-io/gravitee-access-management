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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.repository.gateway.api.VerifyAttemptRepository;
import io.gravitee.am.repository.gateway.api.search.VerifyAttemptCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.VerifyAttemptMongo;
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
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_CLIENT;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_USER_ID;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoVerifyAttemptRepository extends AbstractGatewayMongoRepository implements VerifyAttemptRepository {
    private static final String VERIFY_ATTEMPT = "verify_attempt";
    private static final String FIELD_FACTOR_ID = "factorId";

    private MongoCollection<VerifyAttemptMongo> verifyAttemptCollection;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        verifyAttemptCollection = mongoOperations.getCollection(VERIFY_ATTEMPT, VerifyAttemptMongo.class);
        super.init(verifyAttemptCollection);

        super.createIndex(verifyAttemptCollection, Map.of(new Document(FIELD_USER_ID, 1).append(FIELD_CLIENT, 1).append(FIELD_FACTOR_ID, 1),
                        new IndexOptions().name("u1c1f1")),
                getEnsureIndexOnStart());
    }

    @Override
    public Maybe<VerifyAttempt> findById(String id) {
        return Observable.fromPublisher(verifyAttemptCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<VerifyAttempt> findByCriteria(VerifyAttemptCriteria criteria) {
        return Observable.fromPublisher(verifyAttemptCollection.find(query(criteria)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<VerifyAttempt> create(VerifyAttempt item) {
        VerifyAttemptMongo verifyAttemptMongo = convert(item);
        return Single.fromPublisher(verifyAttemptCollection.insertOne(verifyAttemptMongo)).flatMap(success -> {
            item.setId(verifyAttemptMongo.getId());
            return Single.just(item);
        }).observeOn(Schedulers.computation());
    }

    @Override
    public Single<VerifyAttempt> update(VerifyAttempt item) {
        VerifyAttemptMongo verifyAttemptMongo = convert(item);
        return Single.fromPublisher(verifyAttemptCollection.replaceOne(eq(FIELD_ID, verifyAttemptMongo.getId()), verifyAttemptMongo)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(verifyAttemptCollection.deleteOne(eq(FIELD_ID, id))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(VerifyAttemptCriteria criteria) {
        return Completable.fromPublisher(verifyAttemptCollection.deleteOne(query(criteria))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUser(String userId) {
        return Completable.fromPublisher(verifyAttemptCollection.deleteMany(eq(FIELD_USER_ID, userId))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId, ReferenceType referenceType) {
        return Completable.fromPublisher(verifyAttemptCollection.deleteMany(and(eq(FIELD_REFERENCE_ID, domainId), eq(FIELD_REFERENCE_TYPE, referenceType.name()))))
                .observeOn(Schedulers.computation());
    }

    private Bson query(VerifyAttemptCriteria criteria) {
        final List<Bson> filters = new ArrayList<>();

        if (criteria.client() != null && !criteria.client().isEmpty()) {
            filters.add(eq(FIELD_CLIENT, criteria.client()));
        }

        if (criteria.userId() != null && !criteria.userId().isEmpty()) {
            filters.add(eq(FIELD_USER_ID, criteria.userId()));
        }

        if (criteria.factorId() != null && !criteria.factorId().isEmpty()) {
            filters.add(eq(FIELD_FACTOR_ID, criteria.factorId()));
        }

        return (filters.isEmpty()) ? new BasicDBObject() : and(filters);
    }

    private VerifyAttempt convert(VerifyAttemptMongo rateLimitMongo) {
        if (rateLimitMongo == null) {
            return null;
        }

        final VerifyAttempt verifyAttempt = new VerifyAttempt();
        verifyAttempt.setId(rateLimitMongo.getId());
        verifyAttempt.setUserId(rateLimitMongo.getUserId());
        verifyAttempt.setFactorId(rateLimitMongo.getFactorId());
        verifyAttempt.setClient(rateLimitMongo.getClient());
        verifyAttempt.setAttempts(rateLimitMongo.getAttempts());
        verifyAttempt.setAllowRequest(rateLimitMongo.isAllowRequest());
        verifyAttempt.setUpdatedAt(rateLimitMongo.getUpdatedAt());
        verifyAttempt.setCreatedAt(rateLimitMongo.getCreatedAt());
        verifyAttempt.setReferenceId(rateLimitMongo.getReferenceId());
        verifyAttempt.setReferenceType(rateLimitMongo.getReferenceType());

        return verifyAttempt;
    }

    private VerifyAttemptMongo convert(VerifyAttempt verifyAttempt) {
        final VerifyAttemptMongo verifyAttemptMongo = new VerifyAttemptMongo();
        verifyAttemptMongo.setId(verifyAttempt.getId() != null ? verifyAttempt.getId() : RandomString.generate());
        verifyAttemptMongo.setUserId(verifyAttempt.getUserId());
        verifyAttemptMongo.setClient(verifyAttempt.getClient());
        verifyAttemptMongo.setFactorId(verifyAttempt.getFactorId());
        verifyAttemptMongo.setAttempts(verifyAttempt.getAttempts());
        verifyAttemptMongo.setAllowRequest(verifyAttempt.isAllowRequest());
        verifyAttemptMongo.setCreatedAt(verifyAttempt.getCreatedAt());
        verifyAttemptMongo.setUpdatedAt(verifyAttempt.getUpdatedAt());
        verifyAttemptMongo.setReferenceId(verifyAttempt.getReferenceId());
        verifyAttemptMongo.setReferenceType(verifyAttempt.getReferenceType());
        return verifyAttemptMongo;
    }
}
