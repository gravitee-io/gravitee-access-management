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

import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CachedJWKSetFetcherTest {

    private static final String JWKS_URI_A = "https://idp-a.example.com/jwks";
    private static final String JWKS_URI_B = "https://idp-b.example.com/jwks";

    @Mock
    private JWKSetFetcher delegate;

    private CachedJWKSetFetcher cache;

    @Before
    public void setUp() {
        cache = new CachedJWKSetFetcher(delegate, 100L, Duration.ofMinutes(5));
    }

    @Test
    public void getKeys_firstCall_delegatesToWrappedFetcher() {
        JWKSetFetchResponse response = response("key-a");
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.just(response));

        TestObserver<JWKSetFetchResponse> testObserver = cache.getKeys(JWKS_URI_A).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(response);
        verify(delegate, times(1)).getKeys(JWKS_URI_A);
    }

    @Test
    public void getKeys_secondCallSameUri_servesFromCache() {
        JWKSetFetchResponse response = response("key-a");
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.just(response));

        cache.getKeys(JWKS_URI_A).test().assertComplete();
        TestObserver<JWKSetFetchResponse> secondCall = cache.getKeys(JWKS_URI_A).test();

        secondCall.assertComplete();
        secondCall.assertValue(response);
        verify(delegate, times(1)).getKeys(JWKS_URI_A);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void getKeys_distinctUris_areCachedSeparately() {
        JWKSetFetchResponse responseA = response("key-a");
        JWKSetFetchResponse responseB = response("key-b");
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.just(responseA));
        when(delegate.getKeys(JWKS_URI_B)).thenReturn(Maybe.just(responseB));

        cache.getKeys(JWKS_URI_A).test().assertValue(responseA);
        cache.getKeys(JWKS_URI_B).test().assertValue(responseB);
        cache.getKeys(JWKS_URI_A).test().assertValue(responseA);
        cache.getKeys(JWKS_URI_B).test().assertValue(responseB);

        verify(delegate, times(1)).getKeys(JWKS_URI_A);
        verify(delegate, times(1)).getKeys(JWKS_URI_B);
    }

    @Test
    public void getKeys_delegateEmpty_notCached() {
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.empty());

        cache.getKeys(JWKS_URI_A).test().assertComplete().assertNoValues();
        cache.getKeys(JWKS_URI_A).test().assertComplete().assertNoValues();

        verify(delegate, times(2)).getKeys(JWKS_URI_A);
    }

    @Test
    public void getKeys_delegateError_propagatesAndDoesNotCache() {
        when(delegate.getKeys(JWKS_URI_A))
                .thenReturn(Maybe.error(new InvalidClientMetadataException("boom")));

        cache.getKeys(JWKS_URI_A).test().assertError(InvalidClientMetadataException.class);
        cache.getKeys(JWKS_URI_A).test().assertError(InvalidClientMetadataException.class);

        verify(delegate, times(2)).getKeys(JWKS_URI_A);
    }

    @Test
    public void getKeys_expiresAfterWrite() {
        CachedJWKSetFetcher shortLived = new CachedJWKSetFetcher(delegate, 100L, Duration.ofMillis(50));
        JWKSetFetchResponse response = response("key-a");
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.just(response));

        shortLived.getKeys(JWKS_URI_A).test().assertValue(response);

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    shortLived.getKeys(JWKS_URI_A).test().assertValue(response);
                    verify(delegate, times(2)).getKeys(JWKS_URI_A);
                });
    }

    @Test
    public void getKeys_maximumSizeEnforced() {
        CachedJWKSetFetcher tinyCache = new CachedJWKSetFetcher(delegate, 1L, Duration.ofMinutes(5));
        JWKSetFetchResponse responseA = response("key-a");
        JWKSetFetchResponse responseB = response("key-b");
        when(delegate.getKeys(JWKS_URI_A)).thenReturn(Maybe.just(responseA));
        when(delegate.getKeys(JWKS_URI_B)).thenReturn(Maybe.just(responseB));

        tinyCache.getKeys(JWKS_URI_A).test().assertValue(responseA);
        tinyCache.getKeys(JWKS_URI_B).test().assertValue(responseB);
        // entry A should have been evicted by B (maximumSize = 1)
        tinyCache.getKeys(JWKS_URI_A).test().assertValue(responseA);

        verify(delegate, times(2)).getKeys(JWKS_URI_A);
        verify(delegate, times(1)).getKeys(JWKS_URI_B);
    }

    private static JWKSetFetchResponse response(String kid) {
        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(kid);
        key.setUse("sig");
        JWKSet set = new JWKSet();
        set.setKeys(Collections.singletonList(key));
        return new JWKSetFetchResponse(set, null);
    }
}
