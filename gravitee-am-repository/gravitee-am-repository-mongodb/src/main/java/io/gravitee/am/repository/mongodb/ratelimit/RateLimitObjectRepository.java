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
package io.gravitee.am.repository.mongodb.ratelimit;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.gravitee.am.repository.mongodb.ratelimit.model.RateLimitMongo;
import io.gravitee.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.ReturnDocument.AFTER;

@Component
@Slf4j
public class RateLimitObjectRepository extends AbstractMongoRepository implements RateLimitRepository<RateLimit> {

    private static final String RATE_LIMIT_API = "ratelimit_api";
    private MongoCollection<RateLimitMongo> rateLimitCollection;

    @Autowired
    @Qualifier("ratelimitMongoTemplate")
    protected MongoDatabase mongoOperations;

    @PostConstruct
    public void init() {
        rateLimitCollection = mongoOperations.getCollection(RATE_LIMIT_API, RateLimitMongo.class);
        super.init(rateLimitCollection);
    }

    @Override
    public Single<RateLimit> incrementAndGet(String key, long weight, Supplier<RateLimit> supplier) {
        return findNotExpiredById(key, weight, supplier)
                .doOnSuccess(rl -> log.debug("Incrementing rate limit entry for key {} with weight {}", rl.getKey(), weight))
                .switchIfEmpty(createNew(supplier, weight)
                        .doOnSuccess(rl -> log.debug("Creating new rate limit entry for key {} with weight {}", rl.getKey(), weight))
                );
    }

private Maybe<RateLimit> findNotExpiredById(String key, long weight, Supplier<RateLimit> sup) {
    return Maybe.fromPublisher(
            rateLimitCollection.findOneAndUpdate(
                    eq("_id", key),
                    new Document("$inc", new Document("counter", weight)),
                    new FindOneAndUpdateOptions().returnDocument(AFTER)
            )
    )
    .map(this::toEntity)
    .flatMap(rateLimit -> {
        if (rateLimit.hasNotExpired()) {
            // still valid → return as-is
            return Maybe.just(rateLimit);
        } else {
            // expired → reset fields like in MongoRateLimitRepository
            RateLimit newRateLimit = new RateLimit(rateLimit.getKey());
            newRateLimit.setCounter(weight);
            newRateLimit.setResetTime(sup.get().getResetTime());
            newRateLimit.setLimit(rateLimit.getLimit());
            newRateLimit.setSubscription(rateLimit.getSubscription());

            RateLimitMongo newRateLimitMongo = toMongoEntity(newRateLimit);

            return Maybe.fromPublisher(
                    rateLimitCollection.findOneAndUpdate(
                            eq("_id", key),
                            new Document("$set", new Document("counter", newRateLimit.getCounter())
                                .append("resetTime", newRateLimit.getResetTime())
                                .append("limit", newRateLimit.getLimit())
                                .append("subscription", newRateLimit.getSubscription())),
                            new FindOneAndUpdateOptions().returnDocument(AFTER)
                    )
            ).map(this::toEntity);
        }
    });
}


    private Single<RateLimit> createNew(Supplier<RateLimit> supplier, long weight) {
        return Single.fromSupplier(supplier::get)
                .flatMap(newRateLimit -> {
                    // Rate limit doesn't exist, create a new one using the supplier
                    newRateLimit.setCounter(weight); // Start with the weight

                    RateLimitMongo newRateLimitMongo = toMongoEntity(newRateLimit);
                    return Single.fromPublisher(rateLimitCollection.insertOne(newRateLimitMongo))
                            .map(rl -> newRateLimit);
                });
    }

    private RateLimit toEntity(RateLimitMongo entity) {
        if (entity == null) return null;
        RateLimit rateLimit = new RateLimit(entity.getKey());
        rateLimit.setCounter(entity.getCounter());
        rateLimit.setResetTime(entity.getResetTime());
        rateLimit.setLimit(entity.getLimit());
        rateLimit.setSubscription(entity.getSubscription());
        return rateLimit;
    }

    private RateLimitMongo toMongoEntity(RateLimit entity) {
        if (entity == null) return null;
        RateLimitMongo rateLimitMongo = new RateLimitMongo();
        rateLimitMongo.setKey(entity.getKey());
        rateLimitMongo.setCounter(entity.getCounter());
        rateLimitMongo.setResetTime(entity.getResetTime());
        rateLimitMongo.setLimit(entity.getLimit());
        rateLimitMongo.setSubscription(entity.getSubscription());
        return rateLimitMongo;
    }
}
