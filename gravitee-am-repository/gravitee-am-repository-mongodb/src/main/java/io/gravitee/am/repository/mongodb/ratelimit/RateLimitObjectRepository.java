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

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
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

import java.time.Instant;
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
                .doOnSuccess(rl -> log.debug("Incrementing rate limit entry for key {} with weight {}", rl.getKey(), weight));
    }

    private Single<RateLimit> findNotExpiredById(String key, long weight, Supplier<RateLimit> supplier) {
        return Maybe.fromPublisher(
                        rateLimitCollection.findOneAndUpdate(
                                Filters.and(
                                        Filters.eq("_id", key),
                                        Filters.gt("resetTime", Instant.now().toEpochMilli())
                                ),
                                Updates.inc("counter", weight),
                                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                        )
                )
                .switchIfEmpty(
                        Maybe.fromPublisher(
                                rateLimitCollection.findOneAndUpdate(
                                        Filters.eq("_id", key),
                                        Updates.combine(
                                                Updates.set("counter", weight),
                                                Updates.set("resetTime", supplier.get().getResetTime()),
                                                Updates.set("limit", supplier.get().getLimit()),
                                                Updates.set("subscription", supplier.get().getSubscription())
                                        ),
                                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
                                )
                        )
                )
                .toSingle()
                .map(this::toEntity);
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
}
