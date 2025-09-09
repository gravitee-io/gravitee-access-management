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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.function.Supplier;

@Repository
public class JdbcRateLimitApiRepository extends AbstractJdbcRepository implements RateLimitRepository<RateLimit> {

    @Autowired
    private SpringRateLimitApiRepository requestObjectRepository;

    @Override
    public Single<RateLimit> incrementAndGet(String key, long weight, Supplier<RateLimit> supplier) {
        return findNotExpiredById(key)
                .doOnSuccess(rl ->  rl.setCounter(rl.getCounter() + weight))
                .doOnSuccess(rl -> LOGGER.debug("Incrementing rate limit entry for key {} with weight {}", rl.getKey(), weight))
                .compose(this::update)

                .switchIfEmpty(createNew(weight, supplier)
                        .doOnSuccess(rl -> LOGGER.debug("Creating new rate limit entry for key {} with weight {}", rl.getKey(), weight))
                        .compose(this::insert));
    }

    private Single<RateLimit> insert(Single<RateLimit> rateLimit) {
        return rateLimit
                .map(this::toJdbcEntity)
                .flatMap(entity -> Single.fromPublisher(getTemplate().insert(entity)))
                .map(this::toEntity);
    }

    private Maybe<RateLimit> update(Maybe<RateLimit> rateLimit) {
        return rateLimit
                .map(this::toJdbcEntity)
                .flatMapSingle(requestObjectRepository::save)
                .map(this::toEntity);
    }

    private Maybe<RateLimit> findNotExpiredById(String key){
        return requestObjectRepository
                .findById(key)
                .map(this::toEntity)
                .filter(RateLimit::hasNotExpired);
    }

    private Single<RateLimit> createNew(long weight, Supplier<RateLimit> supplier){
        return Single.fromSupplier(supplier::get)
                .doOnSuccess(rl -> rl.setCounter(weight));
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
