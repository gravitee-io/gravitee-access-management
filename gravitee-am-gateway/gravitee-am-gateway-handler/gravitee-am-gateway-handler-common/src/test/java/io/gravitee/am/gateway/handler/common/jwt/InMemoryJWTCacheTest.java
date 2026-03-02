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
package io.gravitee.am.gateway.handler.common.jwt;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InMemoryJWTCacheTest {

    @Test
    public void shouldStoreAndReturnCachedToken() {
        InMemoryJWTCache cache = InMemoryJWTCache.builder()
                .maxSize(10)
                .expireAfterWrite(Duration.ofSeconds(10))
                .build();

        TestObserver<Boolean> missObserver = cache.isPresent("token").test();
        missObserver.assertResult(false);

        cache.put("token", 10L);

        TestObserver<Boolean> hitObserver = cache.isPresent("token").test();
        hitObserver.assertResult(true);
    }

    @Test
    public void shouldReportStatsWhenEnabled() {
        List<InMemoryJWTCache.CacheStats> stats = new ArrayList<>();
        InMemoryJWTCache cache = InMemoryJWTCache.builder()
                .maxSize(2)
                .expireAfterWrite(Duration.ofSeconds(10))
                .statsConsumer(stats::add)
                .build();

        cache.isPresent("token").test().assertResult(false);
        cache.put("token", 10L);
        cache.isPresent("token").test().assertResult(true);

        assertEquals(3, stats.size());
        InMemoryJWTCache.CacheStats lastStats = stats.get(stats.size() - 1);
        assertEquals(1L, lastStats.currentSize());
        assertEquals(2L, lastStats.maxSize());
        assertEquals(0.5, lastStats.hitRate(), 0.0001);
        assertEquals(0.5, lastStats.missRate(), 0.0001);
    }

    @Test
    public void shouldKeepNoOpCacheEmpty() {
        JWTCache cache = new JWTCache.NoOpJtiCache();

        cache.isPresent("token").test().assertResult(false);
        cache.put("token", 10L);
        cache.isPresent("token").test().assertResult(false);
    }

    @Test
    public void shouldStoreCollidingHashTokensIndependently() {
        InMemoryJWTCache cache = InMemoryJWTCache.builder()
                .maxSize(10)
                .expireAfterWrite(Duration.ofSeconds(10))
                .build();

        String firstToken = "Aa";
        String secondToken = "BB";

        assertEquals(firstToken.hashCode(), secondToken.hashCode());

        cache.put(firstToken, 10L);
        cache.put(secondToken, 10L);

        cache.isPresent(firstToken).test().assertResult(true);
        cache.isPresent(secondToken).test().assertResult(true);
    }
}
