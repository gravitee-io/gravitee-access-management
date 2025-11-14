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
import io.gravitee.secrets.api.core.SecretURL;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.times;
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

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Test
    public void shouldResolveAndCacheSecretValue() {
        CachingSecretResolver cachingResolver = new CachingSecretResolver(delegate, null, null);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        when(delegate.resolveSecretFromUrl(secretURL)).thenReturn(Single.just("secretValue"));

        cachingResolver.resolveSecretFromUrl(secretURL)
            .test()
            .assertComplete()
            .assertResult("secretValue");

        cachingResolver.resolveSecretFromUrl(secretURL)
            .test()
            .assertComplete()
            .assertResult("secretValue");

        verify(delegate, times(1)).resolveSecretFromUrl(secretURL);
    }

    @Test
    public void shouldEvictSecretValueAfterTtlPasses() {
        CachingSecretResolver cachingResolver = new CachingSecretResolver(delegate, SHORT_CACHE_TTL, null);
        scheduleEnforcedCleanup(cachingResolver);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        when(delegate.resolveSecretFromUrl(secretURL)).thenReturn(Single.just("secretValue"));

        TestObserver<SecretURL> evictionsObserver = cachingResolver.evictions().test();

        cachingResolver.resolveSecretFromUrl(secretURL)
                .test()
                .assertComplete()
                .assertResult("secretValue");

        evictionsObserver
            .awaitCount(1)
            .assertValues(secretURL);

        cachingResolver.resolveSecretFromUrl(secretURL)
                .test()
                .assertComplete()
                .assertResult("secretValue");

        verify(delegate, times(2)).resolveSecretFromUrl(secretURL);
    }

    @Test
    public void shouldEvictSecretValueAfterMaxSizeReached() {
        CachingSecretResolver cachingResolver = new CachingSecretResolver(delegate, null, 1L);
        scheduleEnforcedCleanup(cachingResolver);

        SecretURL secretUrl1 = new SecretURL("validProvider", "secretPath", "secretKey1", ArrayListMultimap.create(), false);
        SecretURL secretUrl2 = new SecretURL("validProvider", "secretPath", "secretKey2", ArrayListMultimap.create(), false);

        when(delegate.resolveSecretFromUrl(secretUrl1)).thenReturn(Single.just("secretValue"));
        when(delegate.resolveSecretFromUrl(secretUrl2)).thenReturn(Single.just("otherSecretValue"));

        TestObserver<SecretURL> evictionsObserver = cachingResolver.evictions().test();

        cachingResolver.resolveSecretFromUrl(secretUrl1)
                .test()
                .assertComplete()
                .assertResult("secretValue");

        cachingResolver.resolveSecretFromUrl(secretUrl2)
                .test()
                .assertComplete()
                .assertResult("otherSecretValue");

        evictionsObserver
                .awaitCount(1)
                .assertValue(secretUrl1);
    }

    private void scheduleEnforcedCleanup(CachingSecretResolver cachingResolver) {
        executor.scheduleAtFixedRate(
                cachingResolver::triggerCleanUp,
                0,
                CLEANUP_ENFORCE_RATE.getNano(),
                TimeUnit.NANOSECONDS
        );
    }
}
