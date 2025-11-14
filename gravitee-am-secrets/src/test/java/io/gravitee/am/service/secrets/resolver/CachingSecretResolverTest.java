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

import com.google.common.collect.ArrayListMultimap;
import io.gravitee.am.service.secrets.cache.SecretsCache;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class CachingSecretResolverTest {

    private static final Duration SHORT_CACHE_TTL = Duration.ofMillis(1);
    private static final Duration CLEANUP_ENFORCE_RATE = Duration.ofMillis(10);

    @Mock
    private SecretResolver delegate;

    @Mock
    private SecretsCache cache;

    @Test
    public void shouldResolveAndCacheSecretValue() {
        CachingSecretResolver cachingResolver = new CachingSecretResolver(delegate, cache);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        when(cache.get(secretURL)).thenReturn(Optional.empty());
        when(delegate.resolveSecretFromUrl(secretURL)).thenReturn(Single.just(new Secret("secretValue")));

        cachingResolver.resolveSecretFromUrl(secretURL)
            .test()
            .assertComplete()
            .assertResult(new Secret("secretValue"));

        verify(cache).put(secretURL, new Secret("secretValue"));
        verify(delegate).resolveSecretFromUrl(secretURL);
    }

    @Test
    public void shouldReuseValueFromCache() {
        CachingSecretResolver cachingResolver = new CachingSecretResolver(delegate, cache);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        when(cache.get(secretURL)).thenReturn(Optional.of(new Secret("secretValue")));

        cachingResolver.resolveSecretFromUrl(secretURL)
                .test()
                .assertComplete()
                .assertResult(new Secret("secretValue"));

        verify(delegate, never()).resolveSecretFromUrl(secretURL);
    }
}
