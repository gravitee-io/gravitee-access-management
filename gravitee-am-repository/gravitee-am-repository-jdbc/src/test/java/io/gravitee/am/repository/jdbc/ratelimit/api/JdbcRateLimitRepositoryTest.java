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

import io.gravitee.am.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.am.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig
public class JdbcRateLimitRepositoryTest {

    @Autowired
    private RateLimitRepository<RateLimit> rateLimitRepository;

    @Test
    public void testIncrementAndGet_NewRateLimit() {
        String key = "test-key-" + System.currentTimeMillis();
        long weight = 5L;
        
        Supplier<RateLimit> supplier = () -> {
            RateLimit rateLimit = new RateLimit(key);
            rateLimit.setCounter(0);
            rateLimit.setLimit(100);
            rateLimit.setResetTime(Instant.now().plusSeconds(3600).toEpochMilli());
            rateLimit.setSubscription("test-subscription");
            return rateLimit;
        };

        RateLimit result = rateLimitRepository.incrementAndGet(key, weight, supplier).blockingGet();

        assertNotNull(result);
        assertEquals(key, result.getKey());
        assertEquals(weight, result.getCounter());
        assertEquals(100, result.getLimit());
        assertEquals("test-subscription", result.getSubscription());
    }

    @Test
    public void testIncrementAndGet_ExistingRateLimit() {
        String key = "test-key-existing-" + System.currentTimeMillis();
        long weight = 3L;
        
        Supplier<RateLimit> supplier = () -> {
            RateLimit rateLimit = new RateLimit(key);
            rateLimit.setCounter(0);
            rateLimit.setLimit(100);
            rateLimit.setResetTime(Instant.now().plusSeconds(3600).toEpochMilli());
            rateLimit.setSubscription("test-subscription");
            return rateLimit;
        };

        // First call - create new rate limit
        RateLimit firstResult = rateLimitRepository.incrementAndGet(key, weight, supplier).blockingGet();
        assertEquals(weight, firstResult.getCounter());

        // Second call - increment existing rate limit
        RateLimit secondResult = rateLimitRepository.incrementAndGet(key, weight, supplier).blockingGet();
        assertEquals(weight * 2, secondResult.getCounter());
    }

    @Test
    public void testIncrementAndGet_ExpiredRateLimit() {
        String key = "test-key-expired-" + System.currentTimeMillis();
        long weight = 2L;
        
        Supplier<RateLimit> supplier = () -> {
            RateLimit rateLimit = new RateLimit(key);
            rateLimit.setCounter(0);
            rateLimit.setLimit(100);
            rateLimit.setResetTime(Instant.now().plusSeconds(3600).toEpochMilli());
            rateLimit.setSubscription("test-subscription");
            return rateLimit;
        };

        // Create a rate limit that expires in the past
        RateLimit expiredRateLimit = new RateLimit(key);
        expiredRateLimit.setCounter(50);
        expiredRateLimit.setLimit(100);
        expiredRateLimit.setResetTime(Instant.now().minusSeconds(1).toEpochMilli()); // Expired
        expiredRateLimit.setSubscription("old-subscription");

        // This should create a new rate limit since the existing one is expired
        RateLimit result = rateLimitRepository.incrementAndGet(key, weight, supplier).blockingGet();

        assertNotNull(result);
        assertEquals(key, result.getKey());
        assertEquals(weight, result.getCounter()); // Should start with weight, not 50 + weight
        assertEquals("test-subscription", result.getSubscription()); // Should use new subscription
    }
}
