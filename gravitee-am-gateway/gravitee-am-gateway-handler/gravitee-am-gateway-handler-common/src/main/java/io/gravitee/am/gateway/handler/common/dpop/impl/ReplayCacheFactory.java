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
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cache.ValueMapper;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ReplayCacheFactory {

    private static final String CACHE_NAME_PREFIX = "dpopReplayCache-";
    private static final Set<String> REDIS_CACHE_TYPES = Set.of("redis", "cache-redis");
    private static final ValueMapper<String, String> IDENTITY_VALUE_MAPPER = new ValueMapper<>() {
        @Override
        public String toCachedValue(String value) {
            return value;
        }

        @Override
        public String toValue(String cachedValue) {
            return cachedValue;
        }
    };

    private ReplayCacheFactory() {
    }

    public static ReplayCache create(boolean enabled, CacheManager cacheManager, String domainId, long maxSize, long replayTtlSeconds, String cacheType) {
        if (!enabled) {
            return new ReplayCache.NoOpReplayCache();
        }
        CacheConfiguration configuration = CacheConfiguration.builder()
            .maxSize(maxSize)
            .timeToLiveInMs(TimeUnit.SECONDS.toMillis(replayTtlSeconds))
            .build();
        Cache<String, String> cache = cacheManager.getOrCreateCache(CACHE_NAME_PREFIX + domainId, configuration, IDENTITY_VALUE_MAPPER);
        return new ReplayCacheImpl(cache, replayTtlSeconds, REDIS_CACHE_TYPES.contains(cacheType));
    }
}
