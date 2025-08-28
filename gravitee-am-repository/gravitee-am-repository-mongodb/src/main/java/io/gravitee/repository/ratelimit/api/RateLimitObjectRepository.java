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

package io.gravitee.repository.ratelimit.api;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.gravitee.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
@Component
public class RateLimitObjectRepository extends AbstractMongoRepository implements RateLimitRepository<RateLimit> {

    private static final String RATE_LIMIT_API = "ratelimit_api";
    private MongoCollection<RateLimit> rateLimitCollection;

    @Autowired
    @Qualifier("ratelimitMongoTemplate")
    protected MongoDatabase mongoOperations;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @PostConstruct
    public void init() {
        rateLimitCollection = mongoOperations.getCollection(RATE_LIMIT_API, RateLimit.class);
        super.init(rateLimitCollection);
    }

    @Override
    public Single<RateLimit> incrementAndGet(String key, long weight, Supplier<RateLimit> supplier) {
        return Observable.fromPublisher(
                rateLimitCollection.findOneAndUpdate(
                        eq("_id", key),
                        new Document("$inc", new Document("counter", weight)),
                        new com.mongodb.client.model.FindOneAndUpdateOptions().returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
                )
        )
        .firstElement()
        .map(this::toEntity)
        .switchIfEmpty(Single.fromCallable(() -> {
            // Rate limit doesn't exist, create a new one using the supplier
            RateLimit newRateLimit = supplier.get();
            newRateLimit.setCounter(weight); // Start with the weight

            RateLimit newRateLimitMongo = toMongoEntity(newRateLimit);
            return Observable.fromPublisher(rateLimitCollection.insertOne(newRateLimitMongo))
                    .flatMap(success -> Observable.just(newRateLimit))
                    .blockingFirst();
        }))
        .flatMap(rateLimit -> {
            // Check if the rate limit has expired
            if (System.currentTimeMillis() > rateLimit.getResetTime()) {
                // Rate limit has expired, create a new one using the supplier
                RateLimit newRateLimit = supplier.get();
                newRateLimit.setCounter(weight); // Start with the weight

                RateLimit newRateLimitMongo = toMongoEntity(newRateLimit);
                return Observable.fromPublisher(rateLimitCollection.replaceOne(eq("_id", key), newRateLimitMongo))
                        .flatMap(success -> Observable.just(newRateLimit))
                        .firstOrError();
            } else {
                return Single.just(rateLimit);
            }
        });
    }

    private RateLimit toEntity(RateLimit entity) {
        if (entity == null) return null;
        RateLimit rateLimit = new RateLimit(entity.getKey());
        rateLimit.setCounter(entity.getCounter());
        rateLimit.setResetTime(entity.getResetTime());
        rateLimit.setLimit(entity.getLimit());
        rateLimit.setSubscription(entity.getSubscription());
        return rateLimit;
    }

    private RateLimit toMongoEntity(RateLimit entity) {
        if (entity == null) return null;
        RateLimit rateLimitMongo = new RateLimit(entity.getKey());
//        rateLimitMongo.se(entity.getKey());
        rateLimitMongo.setCounter(entity.getCounter());
        rateLimitMongo.setResetTime(entity.getResetTime());
        rateLimitMongo.setLimit(entity.getLimit());
        rateLimitMongo.setSubscription(entity.getSubscription());
        return rateLimitMongo;
    }
}
