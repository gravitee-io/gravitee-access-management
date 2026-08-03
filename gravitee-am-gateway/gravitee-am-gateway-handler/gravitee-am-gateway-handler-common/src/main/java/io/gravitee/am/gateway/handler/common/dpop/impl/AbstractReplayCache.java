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
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

abstract class AbstractReplayCache implements ReplayCache {

    protected final Cache<String, String> cache;

    private final long replayTtlSeconds;

    protected AbstractReplayCache(Cache<String, String> cache, long replayTtlSeconds) {
        this.cache = cache;
        this.replayTtlSeconds = replayTtlSeconds;
    }

    protected abstract Single<String> claim(String jti, String marker);

    @Override
    public Single<Boolean> register(String jti) {
        return Single.defer(() -> {
            String marker = UUID.randomUUID().toString();
            return claim(jti, marker)
                .map(marker::equals)
                .flatMap(winner -> winner ? applyTtl(jti, marker) : Single.just(false));
        });
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
