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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ReplayCacheImpl implements ReplayCache {

    private final Cache<String, String> cache;
    private final long replayTtlSeconds;
    private final boolean redisFallback;

    public ReplayCacheImpl(Cache<String, String> cache, long replayTtlSeconds, boolean redisFallback) {
        this.cache = cache;
        this.replayTtlSeconds = replayTtlSeconds;
        this.redisFallback = redisFallback;
    }

    @Override
    public Single<Boolean> register(String jti) {
        return Single.defer(() -> {
            String marker = UUID.randomUUID().toString();
            return Single.defer(() -> cache.rxComputeIfAbsent(jti, ignored -> marker).toSingle())
                .onErrorResumeNext(error -> error instanceof UnsupportedOperationException && redisFallback
                    ? registerWithPutIfAbsent(jti, marker)
                    : Single.error(error))
                .map(marker::equals)
                .flatMap(winner -> winner ? applyTtl(jti, marker) : Single.just(false));
        });
    }

    private Single<String> registerWithPutIfAbsent(String jti, String marker) {
        return Single.defer(() -> cache.rxPut(jti, marker)
            .ignoreElement()
            .andThen(Maybe.defer(() -> cache.rxGet(jti)))
            .toSingle());
    }

    private Single<Boolean> applyTtl(String jti, String marker) {
        return Single.defer(() -> cache.rxPut(jti, marker, replayTtlSeconds, TimeUnit.SECONDS)
            .ignoreElement()
            .andThen(Single.just(true))
            .onErrorResumeNext(error -> cache.rxEvict(jti)
                .ignoreElement()
                .onErrorComplete()
                .andThen(Single.<Boolean>error(error))));
    }
}
