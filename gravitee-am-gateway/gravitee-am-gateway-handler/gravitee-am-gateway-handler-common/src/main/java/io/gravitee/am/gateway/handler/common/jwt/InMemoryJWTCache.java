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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.reactivex.rxjava3.core.Single;
import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

public class InMemoryJWTCache implements JWTCache {
    private final Cache<String, Long> cache;
    private final long maxSize;
    private final Consumer<CacheStats> statsConsumer;

    public record CacheStats(long currentSize, long maxSize, double hitRate, double missRate) {
    }

    @Builder
    public InMemoryJWTCache(long maxSize,
                            Duration expireAfterWrite,
                            Consumer<CacheStats> statsConsumer) {
        var builder = CacheBuilder.newBuilder()
                .expireAfterWrite(expireAfterWrite)
                .maximumSize(maxSize);
        this.statsConsumer = statsConsumer;
        this.maxSize = maxSize;
        if(statsConsumer != null) {
            this.cache = builder.recordStats().build();
        } else {
            this.cache = builder.build();
        }
    }

    @Override
    public Single<Boolean> isPresent(String jwt) {
        if(statsConsumer != null) {
            return Single.fromCallable(() -> Objects.nonNull(cache.getIfPresent(hash(jwt))))
                    .doOnSuccess(timer -> statsConsumer.accept(stats()));
        } else {
            return Single.fromCallable(() -> Objects.nonNull(cache.getIfPresent(hash(jwt))));
        }
    }

    @Override
    public void put(String jwt, long expiresAt) {
        String hash = hash(jwt);
        if (cache.asMap().putIfAbsent(hash, expiresAt) == null) {
            if (statsConsumer != null) {
                statsConsumer.accept(stats());
            }
        }
    }

    private static String hash(String jwt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(jwt.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private CacheStats stats() {
        com.google.common.cache.CacheStats stats = cache.stats();
        return new CacheStats(cache.size(), maxSize, stats.hitRate(), stats.missRate());
    }

}
