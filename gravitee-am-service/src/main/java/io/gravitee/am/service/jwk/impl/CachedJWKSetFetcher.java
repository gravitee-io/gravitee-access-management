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
package io.gravitee.am.service.jwk.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.reactivex.rxjava3.core.Maybe;

import java.time.Duration;

public class CachedJWKSetFetcher implements JWKSetFetcher {
    private final JWKSetFetcher jwkSetFetcher;
    private final Cache<String, JWKSetFetchResponse> cache;

    public CachedJWKSetFetcher(JWKSetFetcher jwkSetFetcher,
                               long maximumSize,
                               Duration expireAfterWrite) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .build();
        this.jwkSetFetcher = jwkSetFetcher;
    }

    @Override
    public Maybe<JWKSetFetchResponse> getKeys(String jwksUri) {
        return Maybe.fromCallable(() -> cache.getIfPresent(jwksUri))
                .switchIfEmpty(Maybe.defer(() -> this.jwkSetFetcher.getKeys(jwksUri)).doAfterSuccess(value -> cache.put(jwksUri, value)));
    }

}
