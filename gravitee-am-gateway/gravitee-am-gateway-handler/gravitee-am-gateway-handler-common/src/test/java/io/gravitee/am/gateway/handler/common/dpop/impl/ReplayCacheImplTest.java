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
package io.gravitee.am.gateway.handler.common.dpop.impl;

import io.gravitee.am.gateway.handler.common.dpop.ReplayCache;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheListener;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCacheImplTest {

    private static final long TTL_SECONDS = 30;

    @Test
    void shouldAcceptRegistrationWithNoOpCache() {
        new ReplayCache.NoOpReplayCache().register("jti").test().assertResult(true);
    }

    @Test
    void shouldRegisterRawJtiAtomicallyWhenComputeIsSupported() {
        ThreadSafeTestCache cache = ThreadSafeTestCache.computeCapable();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);

        replayCache.register("raw-jti").test().assertResult(true);
        replayCache.register("raw-jti").test().assertResult(false);

        assertEquals(2, cache.computeCalls.get());
        assertEquals("raw-jti", cache.lastComputeKey);
        assertEquals(1, cache.ttlWrites.get());
        assertEquals("raw-jti", cache.lastTtlKey);
        assertEquals(TTL_SECONDS, cache.lastTtl);
        assertSame(TimeUnit.SECONDS, cache.lastTtlUnit);
    }

    @Test
    void shouldCreateFreshMarkerForEachSubscription() {
        ThreadSafeTestCache cache = ThreadSafeTestCache.computeCapable();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);
        Single<Boolean> registration = replayCache.register("raw-jti");

        registration.test().assertResult(true);
        registration.test().assertResult(false);

        assertEquals(1, cache.ttlWrites.get());
    }

    @Test
    void shouldAllowOneConcurrentComputeRegistration() throws Exception {
        ThreadSafeTestCache cache = ThreadSafeTestCache.computeCapable();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);

        List<Boolean> results = raceRegistrations(replayCache, 32);

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, cache.ttlWrites.get());
    }

    @Test
    void shouldFallbackToRedisSetNxWhenComputeIsUnsupported() {
        CurrentRedisSetNxTestCache cache = new CurrentRedisSetNxTestCache();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, true);

        replayCache.register("raw-jti").test().assertResult(true);
        replayCache.register("raw-jti").test().assertResult(false);

        assertEquals(2, cache.computeCalls.get());
        assertEquals(2, cache.currentRedisSetNxCalls.get());
        assertEquals(2, cache.getCalls.get());
        assertEquals("raw-jti", cache.lastCurrentRedisSetNxKey);
        assertEquals(1, cache.ttlWrites.get());
    }

    @Test
    void shouldAllowOneConcurrentRedisSetNxFallbackRegistration() throws Exception {
        CurrentRedisSetNxTestCache cache = new CurrentRedisSetNxTestCache();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, true);

        List<Boolean> results = raceRegistrations(replayCache, 32);

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, cache.ttlWrites.get());
    }

    @Test
    void shouldPropagateComputeBackendFailureWithoutFallback() {
        IllegalStateException failure = new IllegalStateException("compute failure");
        ThreadSafeTestCache cache = ThreadSafeTestCache.computeCapable();
        cache.computeFailure = failure;
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);

        replayCache.register("jti").test().assertError(error -> error == failure);

        assertEquals(0, cache.ttlWrites.get());
    }

    @Test
    void shouldPropagateRedisSetNxBackendFailure() {
        IllegalStateException failure = new IllegalStateException("put failure");
        CurrentRedisSetNxTestCache cache = new CurrentRedisSetNxTestCache();
        cache.currentRedisSetNxFailure = failure;
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, true);

        replayCache.register("jti").test().assertError(error -> error == failure);

        assertEquals(0, cache.getCalls.get());
        assertEquals(0, cache.ttlWrites.get());
    }

    @Test
    void shouldEvictEntryAndPropagateWhenTtlWriteFails() {
        IllegalStateException failure = new IllegalStateException("ttl failure");
        ThreadSafeTestCache cache = ThreadSafeTestCache.computeCapable();
        cache.ttlFailure = failure;
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);

        replayCache.register("jti").test().awaitDone(5, TimeUnit.SECONDS).assertError(error -> error == failure);

        assertEquals(1, cache.ttlWrites.get());
        assertEquals(1, cache.evictCalls.get());
        assertFalse(cache.containsKey("jti"));
    }

    @Test
    void shouldPropagateUnsupportedComputeWhenFallbackDisabled() {
        CurrentRedisSetNxTestCache cache = new CurrentRedisSetNxTestCache();
        ReplayCache replayCache = new ReplayCacheImpl(cache, TTL_SECONDS, false);

        replayCache.register("jti").test().assertError(UnsupportedOperationException.class);

        assertEquals(1, cache.computeCalls.get());
        assertEquals(0, cache.currentRedisSetNxCalls.get());
        assertEquals(0, cache.getCalls.get());
        assertEquals(0, cache.ttlWrites.get());
    }

    private List<Boolean> raceRegistrations(ReplayCache replayCache, int registrationCount) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(registrationCount)) {
            CountDownLatch ready = new CountDownLatch(registrationCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> registrations = new ArrayList<>();
            try {
                for (int index = 0; index < registrationCount; index++) {
                    registrations.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return replayCache.register("concurrent-jti").blockingGet();
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                List<Boolean> results = new ArrayList<>();
                for (Future<Boolean> registration : registrations) {
                    results.add(registration.get(5, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static class ThreadSafeTestCache implements Cache<String, String> {

        protected final Map<String, String> entries = new ConcurrentHashMap<>();
        protected final AtomicInteger computeCalls = new AtomicInteger();
        protected final AtomicInteger getCalls = new AtomicInteger();
        protected final AtomicInteger ttlWrites = new AtomicInteger();
        private final AtomicInteger evictCalls = new AtomicInteger();
        private volatile RuntimeException computeFailure;
        private volatile RuntimeException ttlFailure;
        protected volatile String lastComputeKey;
        private volatile String lastTtlKey;
        private volatile long lastTtl;
        private volatile TimeUnit lastTtlUnit;

        private static ThreadSafeTestCache computeCapable() {
            return new ThreadSafeTestCache();
        }

        @Override
        public Maybe<String> rxComputeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
            computeCalls.incrementAndGet();
            lastComputeKey = key;
            if (computeFailure != null) {
                return Maybe.error(computeFailure);
            }
            return Maybe.just(entries.computeIfAbsent(key, mappingFunction));
        }

        @Override
        public Maybe<String> rxPut(String key, String value) {
            return Maybe.fromCallable(() -> entries.put(key, value));
        }

        @Override
        public Maybe<String> rxGet(String key) {
            getCalls.incrementAndGet();
            return Maybe.fromOptional(java.util.Optional.ofNullable(entries.get(key)));
        }

        @Override
        public Maybe<String> rxPut(String key, String value, long ttl, TimeUnit timeUnit) {
            ttlWrites.incrementAndGet();
            lastTtlKey = key;
            lastTtl = ttl;
            lastTtlUnit = timeUnit;
            if (ttlFailure != null) {
                return Maybe.error(ttlFailure);
            }
            return Maybe.fromCallable(() -> entries.put(key, value));
        }

        @Override
        public String getName() {
            return "test-cache";
        }

        @Override
        public Collection<String> values() {
            return entries.values();
        }

        @Override
        public Set<String> keys() {
            return entries.keySet();
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return entries.entrySet();
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public boolean containsKey(String key) {
            return entries.containsKey(key);
        }

        @Override
        public String get(String key) {
            return entries.get(key);
        }

        @Override
        public String put(String key, String value) {
            return entries.put(key, value);
        }

        @Override
        public String put(String key, String value, long ttl, TimeUnit timeUnit) {
            return entries.put(key, value);
        }

        @Override
        public void putAll(Map<? extends String, ? extends String> values) {
            entries.putAll(values);
        }

        @Override
        public String computeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
            return entries.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public String computeIfPresent(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return entries.computeIfPresent(key, remappingFunction);
        }

        @Override
        public String compute(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return entries.compute(key, remappingFunction);
        }

        @Override
        public String evict(String key) {
            evictCalls.incrementAndGet();
            return entries.remove(key);
        }

        @Override
        public void clear() {
            entries.clear();
        }

        @Override
        public String addCacheListener(CacheListener<String, String> cacheListener) {
            return "listener";
        }

        @Override
        public boolean removeCacheListener(String listenerId) {
            return true;
        }
    }

    private static final class CurrentRedisSetNxTestCache extends ThreadSafeTestCache {

        private final AtomicInteger currentRedisSetNxCalls = new AtomicInteger();
        private volatile RuntimeException currentRedisSetNxFailure;
        private volatile String lastCurrentRedisSetNxKey;

        @Override
        public Maybe<String> rxComputeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
            computeCalls.incrementAndGet();
            lastComputeKey = key;
            throw new UnsupportedOperationException("computeIfAbsent is not supported");
        }

        @Override
        public Maybe<String> rxPut(String key, String value) {
            currentRedisSetNxCalls.incrementAndGet();
            lastCurrentRedisSetNxKey = key;
            if (currentRedisSetNxFailure != null) {
                return Maybe.error(currentRedisSetNxFailure);
            }
            String previous = entries.putIfAbsent(key, value);
            return previous == null ? Maybe.empty() : Maybe.just(previous);
        }
    }
}
