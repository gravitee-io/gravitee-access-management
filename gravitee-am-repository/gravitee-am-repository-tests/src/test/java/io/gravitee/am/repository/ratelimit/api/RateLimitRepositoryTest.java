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
package io.gravitee.am.repository.ratelimit.api;

import static org.junit.Assert.assertEquals;

import io.gravitee.am.repository.ratelimit.AbstractRateLimitTest;
import io.gravitee.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.observers.TestObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class RateLimitRepositoryTest extends AbstractRateLimitTest {

    private static final long OPERATION_TIMEOUT_SECONDS = 5L;
    private static final Map<String, RateLimit> RATE_LIMITS = new HashMap<>();

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    RateLimitRepository<RateLimit> rateLimitRepository;

    @Override
    protected String getTestCasesPath() {
        return "/data/ratelimit-tests/";
    }

    @Override
    protected String getModelPackage() {
        return "io.gravitee.repository.ratelimit.model.";
    }

    @Override
    protected void createModel(Object object) {
        RateLimit rateLimit = (RateLimit) object;
        RateLimit updatedRateLimit = rateLimitRepository
                .incrementAndGet(rateLimit.getKey(), rateLimit.getCounter(), () -> initialize(rateLimit))
                .blockingGet();

        RATE_LIMITS.put(updatedRateLimit.getKey(), updatedRateLimit);

        log.debug("Created {}", updatedRateLimit);
        RATE_LIMITS.computeIfAbsent(
                rateLimit.getKey(),
                key -> rateLimitRepository.incrementAndGet(key, rateLimit.getCounter(), () -> initialize(rateLimit)).blockingGet()
        );
    }

    @Test
    public void shouldIncrementAndGet_byOne() throws InterruptedException {
        final RateLimit rateLimit = RATE_LIMITS.get("rl-1");
        final TestObserver<RateLimit> observer = incrementAndObserve(rateLimit, 1L);
        final long expectedCounter = 1L;

        observer.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        observer
                .assertValue(shouldNotFail(rl -> assertEquals(expectedCounter, rl.getCounter())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getSubscription(), rl.getSubscription())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getResetTime(), rl.getResetTime())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getLimit(), rl.getLimit())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getKey(), rl.getKey())));
    }

    @Test
    public void shouldIncrementAndGet_fromSuppliedCounterByTwo() throws InterruptedException {
        final RateLimit rateLimit = RATE_LIMITS.get("rl-2");
        final TestObserver<RateLimit> observer = incrementAndObserve(rateLimit, 2L);
        final long expectedCounter = 42L;

        observer.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        observer
                .assertValue(shouldNotFail(rl -> assertEquals(expectedCounter, rl.getCounter())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getSubscription(), rl.getSubscription())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getResetTime(), rl.getResetTime())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getLimit(), rl.getLimit())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getKey(), rl.getKey())));
    }

    @Test
    public void shouldIncrementAndGet_withUnknownKey() throws InterruptedException {
        final RateLimit rateLimit = of("rl-3", 0, 100000, 5000, "rl-3-subscription");
        final TestObserver<RateLimit> observer = incrementAndObserve(rateLimit, 10L);
        final long expectedCounter = 10L;

        observer.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        observer
                .assertValue(shouldNotFail(rl -> assertEquals(expectedCounter, rl.getCounter())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getSubscription(), rl.getSubscription())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getResetTime(), rl.getResetTime())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getLimit(), rl.getLimit())))
                .assertValue(shouldNotFail(rl -> assertEquals(rateLimit.getKey(), rl.getKey())));
    }

    @Test
    public void shouldResetCounterAndUpdateResetTimeWhenExpired() throws InterruptedException {
        // Given an initial rate limit with a reset time that is already in the past
        final RateLimit expiredRateLimit = of("rl-expired", 5, -1000, 10_000, "rl-expired-subscription");

        // Insert into repository with expired reset time - use the expired rate limit directly as supplier
        RATE_LIMITS.put(expiredRateLimit.getKey(), expiredRateLimit);
        rateLimitRepository.incrementAndGet(expiredRateLimit.getKey(), 1L, () -> expiredRateLimit).blockingGet();

        // When we increment again, since the stored rate limit has expired, the repo should reset the counter and push reset_time forward
        final TestObserver<RateLimit> observer = incrementAndObserve(expiredRateLimit, 2L);

        observer.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        observer.assertValue(shouldNotFail(rl -> {
            // Counter should be reset to "weight", not old value + weight
            assertEquals(2L, rl.getCounter());

            // Reset time should now be in the future (from the supplier)
            long now = Instant.now().toEpochMilli();
            assert(rl.getResetTime() > now) : "Expected reset time in the future, got " + rl.getResetTime();

            // Limit and subscription should remain the same
            assertEquals(expiredRateLimit.getLimit(), rl.getLimit());
            assertEquals(expiredRateLimit.getSubscription(), rl.getSubscription());
            assertEquals(expiredRateLimit.getKey(), rl.getKey());
        }));
    }

    @Test
    public void shouldIncrementWithoutResetWhenNotExpired() throws InterruptedException {
        // Given an initial rate limit with a reset time far in the future
        final RateLimit validRateLimit = of("rl-valid", 5, 10_000, 10_000, "rl-valid-subscription");

        // Insert into repository with valid (non-expired) reset time
        RATE_LIMITS.put(validRateLimit.getKey(), validRateLimit);
        rateLimitRepository.incrementAndGet(validRateLimit.getKey(), 1L, () -> validRateLimit).blockingGet();

        // When we increment again, since reset_time > now, the repo should just increment counter
        final TestObserver<RateLimit> observer = incrementAndObserve(validRateLimit, 3L);

        observer.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        observer.assertValue(shouldNotFail(rl -> {
            // Counter should be incremented, not reset
            // First call: counter = 1 (weight), Second call: counter = 1 + 3 = 4
            assertEquals(4L, rl.getCounter());

            // Reset time should remain the same
            assertEquals(validRateLimit.getResetTime(), rl.getResetTime());

            // Limit and subscription should remain unchanged
            assertEquals(validRateLimit.getLimit(), rl.getLimit());
            assertEquals(validRateLimit.getSubscription(), rl.getSubscription());
            assertEquals(validRateLimit.getKey(), rl.getKey());
        }));
    }


    private TestObserver<RateLimit> incrementAndObserve(RateLimit rateLimit, long weight) {
        return rateLimitRepository.incrementAndGet(rateLimit.getKey(), weight, () -> initialize(rateLimit)).test();
    }

    /*
     * Used to get better error messages with testObserver
     * using a consumer that can throw an assertion error
     * if the assertion fails before returning true
     */
    private static Predicate<RateLimit> shouldNotFail(Consumer<RateLimit> consumer) {
        return rl -> {
            consumer.accept(rl);
            return true;
        };
    }

    /**
     *
     * @param key the rateLimit key
     * @param counter the counter value of the rateLimit
     * @param expireIn when the rateLimit should expire in milliseconds
     * @param limit the limit of the rateLimit
     * @param subscription the subscription of the rateLimit
     * @return a new rateLimit
     */
    private static RateLimit of(String key, long counter, long expireIn, long limit, String subscription) {
        final RateLimit rateLimit = new RateLimit(key);
        final Instant resetTime = Instant.now().plus(Duration.ofMillis(expireIn));
        rateLimit.setSubscription(subscription);
        rateLimit.setResetTime(resetTime.toEpochMilli());
        rateLimit.setLimit(limit);
        rateLimit.setCounter(counter);
        return rateLimit;
    }

    /**
     *
     * @param rateLimit a rateLimit to pass as a supplier when initializing our data
     * @return a copy of this rateLimit with a 0 counter
     */
    private static RateLimit initialize(RateLimit rateLimit) {
        final long counter = 0;
        // If the rate limit is expired, create a new one with current time + some duration
        if (!rateLimit.hasNotExpired()) {
            return of(rateLimit.getKey(), counter, 10000, rateLimit.getLimit(), rateLimit.getSubscription());
        }
        // Calculate the duration from now to the reset time
        long durationFromNow = rateLimit.getResetTime() - System.currentTimeMillis();
        return of(rateLimit.getKey(), counter, durationFromNow, rateLimit.getLimit(), rateLimit.getSubscription());
    }
}