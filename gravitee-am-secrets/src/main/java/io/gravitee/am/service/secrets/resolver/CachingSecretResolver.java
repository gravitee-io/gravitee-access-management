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

import io.gravitee.am.service.secrets.cache.SecretsCache;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class CachingSecretResolver implements SecretResolver {

    private final SecretResolver delegate;
    private final SecretsCache cache;

    @Override
    public Single<Secret> resolveSecretFromUrl(SecretURL url) {
        return Single.defer(() -> {
            final Optional<Secret> cached = readFromCache(url);
            if (cached.isPresent()) {
                return Single.just(cached.get());
            }
            return delegate.resolveSecretFromUrl(url)
                    .doOnSuccess(value -> cache.put(url, value));
        });
    }

    /**
     * A cache failure must not break the resolution, fall back to the delegate.
     */
    private Optional<Secret> readFromCache(SecretURL url) {
        try {
            return cache.get(url);
        } catch (Exception e) {
            log.warn("Unable to read secret from cache, resolving it from the provider", e);
            return Optional.empty();
        }
    }
}
