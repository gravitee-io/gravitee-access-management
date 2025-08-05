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
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.management.api.search.RateLimitCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.RateLimitMongo;
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

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRateLimitRepository extends AbstractGatewayMongoRepository implements RateLimitRepository {
    private static final String RATE_LIMIT = "rate_limit";
    private static final String FIELD_FACTOR_ID = "factorId";

    private MongoCollection<RateLimitMongo> rateLimitCollection;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        rateLimitCollection = mongoOperations.getCollection(RATE_LIMIT, RateLimitMongo.class);
        super.init(rateLimitCollection);

        super.createIndex(rateLimitCollection, Map.of(new Document(FIELD_USER_ID, 1)
                        .append(FIELD_CLIENT, 1)
                        .append(FIELD_FACTOR_ID, 1), new IndexOptions().name("u1c1f1")),
                getEnsureIndexOnStart());
    }

    @Override
    public Maybe<RateLimit> findById(String id) {
        return Observable.fromPublisher(rateLimitCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<RateLimit> findByCriteria(RateLimitCriteria criteria) {
        return Observable.fromPublisher(rateLimitCollection.find(query(criteria)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<RateLimit> create(RateLimit item) {
        RateLimitMongo rateLimitMongo = convert(item);
        return Single.fromPublisher(rateLimitCollection.insertOne(rateLimitMongo)).flatMap(success -> {
            item.setId(rateLimitMongo.getId());
            return Single.just(item);
        }).observeOn(Schedulers.computation());
    }

    @Override
    public Single<RateLimit> update(RateLimit item) {
        RateLimitMongo rateLimitMongo = convert(item);
        return Single.fromPublisher(rateLimitCollection.replaceOne(eq(FIELD_ID, rateLimitMongo.getId()), rateLimitMongo)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(rateLimitCollection.deleteOne(eq(FIELD_ID, id))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(RateLimitCriteria criteria) {
        return Completable.fromPublisher(rateLimitCollection.deleteOne(query(criteria))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUser(String userId) {
        return Completable.fromPublisher(rateLimitCollection.deleteMany(eq(FIELD_USER_ID, userId))).observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId, ReferenceType referenceType) {
        return Completable.fromPublisher(rateLimitCollection.deleteMany(and(eq(FIELD_REFERENCE_ID, domainId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))).observeOn(Schedulers.computation());
    }

    private Bson query(RateLimitCriteria criteria) {
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

    private RateLimit convert(RateLimitMongo rateLimitMongo) {
        if (rateLimitMongo == null) {
            return null;
        }

        final RateLimit rateLimit = new RateLimit();
        rateLimit.setId(rateLimitMongo.getId());
        rateLimit.setUserId(rateLimitMongo.getUserId());
        rateLimit.setFactorId(rateLimitMongo.getFactorId());
        rateLimit.setClient(rateLimitMongo.getClient());
        rateLimit.setTokenLeft(rateLimitMongo.getTokenLeft());
        rateLimit.setAllowRequest(rateLimitMongo.isAllowRequest());
        rateLimit.setUpdatedAt(rateLimitMongo.getUpdatedAt());
        rateLimit.setCreatedAt(rateLimitMongo.getCreatedAt());
        rateLimit.setReferenceId(rateLimitMongo.getReferenceId());
        rateLimit.setReferenceType(rateLimitMongo.getReferenceType());

        return rateLimit;
    }

    private RateLimitMongo convert(RateLimit rateLimit) {
        if (rateLimit == null) {
            return null;
        }

        final RateLimitMongo rateLimitMongo = new RateLimitMongo();
        rateLimitMongo.setId(rateLimit.getId() != null ? rateLimit.getId() : RandomString.generate());
        rateLimitMongo.setUserId(rateLimit.getUserId());
        rateLimitMongo.setClient(rateLimit.getClient());
        rateLimitMongo.setFactorId(rateLimit.getFactorId());
        rateLimitMongo.setTokenLeft(rateLimit.getTokenLeft());
        rateLimitMongo.setAllowRequest(rateLimit.isAllowRequest());
        rateLimitMongo.setCreatedAt(rateLimit.getCreatedAt());
        rateLimitMongo.setUpdatedAt(rateLimit.getUpdatedAt());
        rateLimitMongo.setReferenceId(rateLimit.getReferenceId());
        rateLimitMongo.setReferenceType(rateLimit.getReferenceType());
        return rateLimitMongo;
    }
}
