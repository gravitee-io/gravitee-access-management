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
import io.gravitee.am.repository.jdbc.ratelimit.api.spring.SpringRateLimitRepository;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.am.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Repository
public class JdbcRateLimitRepository extends AbstractJdbcRepository implements RateLimitRepository<RateLimit> {

    @Autowired
    private SpringRateLimitRepository requestObjectRepository;

    protected RateLimit toEntity(JdbcRateLimit entity) { 
        if (entity == null) return null;
        RateLimit rateLimit = new RateLimit(entity.getKey());
        rateLimit.setCounter(entity.getCounter());
        rateLimit.setResetTime(entity.getResetTime());
        rateLimit.setLimit(entity.getLimit());
        rateLimit.setSubscription(entity.getSubscription());
        return rateLimit;
    }
    
    protected JdbcRateLimit toJdbcEntity(RateLimit entity) {
        if (entity == null) return null;
        JdbcRateLimit jdbcRateLimit = new JdbcRateLimit();
        jdbcRateLimit.setKey(entity.getKey());
        jdbcRateLimit.setCounter(entity.getCounter());
        jdbcRateLimit.setResetTime(entity.getResetTime());
        jdbcRateLimit.setLimit(entity.getLimit());
        jdbcRateLimit.setSubscription(entity.getSubscription());
        return jdbcRateLimit;
    }

    @Override
    @Transactional
    public Single<RateLimit> incrementAndGet(String key, long weight, Supplier<RateLimit> supplier) {
        LOGGER.debug("Incrementing rate limit for key {} with weight {}", key, weight);
        
        return Single.fromCallable(() -> {
            // Try to find existing rate limit
            JdbcRateLimit existingJdbcRateLimit = requestObjectRepository.findById(key).blockingGet();
            
            if (existingJdbcRateLimit != null) {
                // Convert to domain model
                RateLimit existingRateLimit = toEntity(existingJdbcRateLimit);
                
                // Increment the counter
                existingRateLimit.setCounter(existingRateLimit.getCounter() + weight);
                
                // Check if the rate limit has expired
                if (System.currentTimeMillis() > existingRateLimit.getResetTime()) {
                    // Rate limit has expired, create a new one using the supplier
                    RateLimit newRateLimit = supplier.get();
                    newRateLimit.setCounter(weight); // Start with the weight
                    JdbcRateLimit newJdbcRateLimit = toJdbcEntity(newRateLimit);
                    return toEntity(requestObjectRepository.save(newJdbcRateLimit).blockingGet());
                } else {
                    // Update the existing rate limit
                    JdbcRateLimit updatedJdbcRateLimit = toJdbcEntity(existingRateLimit);
                    return toEntity(requestObjectRepository.save(updatedJdbcRateLimit).blockingGet());
                }
            } else {
                // Create a new rate limit using the supplier
                RateLimit newRateLimit = supplier.get();
                newRateLimit.setCounter(weight); // Start with the weight
                JdbcRateLimit newJdbcRateLimit = toJdbcEntity(newRateLimit);
                return toEntity(requestObjectRepository.save(newJdbcRateLimit).blockingGet());
            }
        });
    }
}
