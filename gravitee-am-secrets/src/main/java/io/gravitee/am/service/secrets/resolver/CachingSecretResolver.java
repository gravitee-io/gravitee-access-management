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
package io.gravitee.am.service.secrets.resolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.gravitee.secrets.api.core.SecretURL;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class CachingSecretResolver implements SecretResolver {

    private final Cache<SecretURL, String> cache;
    private final SecretResolver delegate;

    private final PublishSubject<SecretURL> evictionSubject = PublishSubject.create();

    public CachingSecretResolver(
            SecretResolver delegate,
            @Nullable Duration ttl,
            @Nullable Long maxSize
    ) {
        this.delegate = delegate;

        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        Optional.ofNullable(ttl).ifPresent(cacheBuilder::expireAfterWrite);
        Optional.ofNullable(maxSize).ifPresent(cacheBuilder::maximumSize);

        cacheBuilder.evictionListener((key, value, cause) -> {
            if (cause.wasEvicted()) {
                evictionSubject.onNext((SecretURL) Objects.requireNonNull(key));
            }
        });

        this.cache = cacheBuilder.build();
    }

    @Override
    public Single<String> resolveSecretFromUrl(SecretURL url) {
        String cached = cache.getIfPresent(url);
        if (cached != null) {
            return Single.just(cached);
        }

        return delegate.resolveSecretFromUrl(url)
                .doOnSuccess(value -> cache.put(url, value));
    }

    public Observable<SecretURL> evictions() {
        return evictionSubject.hide();
    }

    public void triggerCleanUp() {
        cache.cleanUp();;
    }
}
