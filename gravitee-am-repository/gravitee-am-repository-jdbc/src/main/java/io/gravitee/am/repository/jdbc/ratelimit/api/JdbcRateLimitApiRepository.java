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

package io.gravitee.am.repository.jdbc.ratelimit.api;

import io.gravitee.am.repository.jdbc.ratelimit.api.model.JdbcRateLimit;
import io.gravitee.am.repository.jdbc.ratelimit.api.spring.SpringRateLimitApiRepository;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.function.Supplier;

@Repository
public class JdbcRateLimitApiRepository extends AbstractJdbcRepository implements RateLimitRepository<RateLimit> {

    @Autowired
    private SpringRateLimitApiRepository requestObjectRepository;

    @Override
    public Single<RateLimit> incrementAndGet(String key, long weight, Supplier<RateLimit> supplier) {
        LOGGER.debug("Rate limit request: key={}, weight={}, currentTime={}", key, weight, System.currentTimeMillis());
        return findById(key)
                .flatMapSingle(rateLimit -> {
                    if (rateLimit.hasNotExpired()) {
                        // Rate limit is still valid, increment counter
                        final RateLimit updatedRateLimit = RateLimit.RateLimitBuilder
                                .builder(rateLimit)
                                .counter(rateLimit.getCounter() + weight)
                                .build();
                        LOGGER.debug("Found and incrementing existing rate limit: key={}, oldCounter={}, newCounter={}, resetTime={}", 
                            key, rateLimit.getCounter(), updatedRateLimit.getCounter(), rateLimit.getResetTime());
                        return update(updatedRateLimit);
                    } else {
                        // Rate limit has expired, update it with new values
                        LOGGER.debug("Rate limit expired for key={}, resetTime={}, currentTime={}, creating new rate limit",
                            key, rateLimit.getResetTime(), System.currentTimeMillis());
                        return createNew(weight, supplier)
                                .doOnSuccess(rl -> LOGGER.debug("Created new rate limit entry for key {} with weight {}", rl.getKey(), weight))
                                .flatMap(this::update);
                    }
                })
                .switchIfEmpty(createNew(weight, supplier)
                        .doOnSuccess(rl -> LOGGER.debug("Creating new rate limit entry for key {} with weight {}", rl.getKey(), weight))
                        .compose(this::insert))
                .observeOn(Schedulers.computation())
                .doOnSuccess(rl -> LOGGER.debug("Rate limit result: key={}, counter={}, resetTime={}, limit={}", 
                    rl.getKey(), rl.getCounter(), rl.getResetTime(), rl.getLimit()));
    }

    private Single<RateLimit> insert(Single<RateLimit> rateLimit) {
        return rateLimit
                .map(this::toJdbcEntity)
                .flatMap(entity -> Single.fromPublisher(getTemplate().insert(entity)))
                .map(this::toEntity);
    }

    private Single<RateLimit> update(RateLimit rateLimit) {
        return Single.just(rateLimit)
                .map(this::toJdbcEntity)
                .flatMap(requestObjectRepository::save)
                .map(this::toEntity);
    }

    private Maybe<RateLimit> findById(String key){
        return requestObjectRepository
                .findById(key)
                .map(this::toEntity);
    }

    private Single<RateLimit> createNew(long weight, Supplier<RateLimit> supplier){
        return Single.fromSupplier(supplier::get)
                .map(rl -> {
                    LOGGER.debug("Supplier created rate limit: key={}, resetTime={}, limit={}, timeWindow={}ms", 
                        rl.getKey(), rl.getResetTime(), rl.getLimit(), 
                        rl.getResetTime() - System.currentTimeMillis());
                    rl.setCounter(weight);
                    return rl;
                });
    }

    private Completable deleteById(String key) {
        return requestObjectRepository.deleteById(key);
    }

    protected RateLimit toEntity(JdbcRateLimit entity) {
        RateLimit rateLimit = new RateLimit(entity.getId());
        rateLimit.setCounter(entity.getCounter());
        rateLimit.setResetTime(entity.getResetTime());
        rateLimit.setLimit(entity.getLimit());
        rateLimit.setSubscription(entity.getSubscription());
        return rateLimit;
    }

    protected JdbcRateLimit toJdbcEntity(RateLimit entity) {
        JdbcRateLimit jdbcRateLimit = new JdbcRateLimit();
        jdbcRateLimit.setId(entity.getKey());
        jdbcRateLimit.setCounter(entity.getCounter());
        jdbcRateLimit.setResetTime(entity.getResetTime());
        jdbcRateLimit.setLimit(entity.getLimit());
        jdbcRateLimit.setSubscription(entity.getSubscription());
        return jdbcRateLimit;
    }

}
