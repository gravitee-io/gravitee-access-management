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
package io.gravitee.am.gateway.handler.oidc.service.spiffe.impl;

import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustBundleServiceImplTest {

    // example.com is an IANA-reserved real domain that always resolves; we want the
    // URL safety check to pass so the test can exercise the fetch/cache logic.
    private static final String JWKS_URL = "https://example.com/keys";

    @Mock
    private JWKService jwkService;
    @Mock
    private Domain domain;

    private SpiffeDomainSettings settings;

    @BeforeEach
    void setUp() {
        settings = new SpiffeDomainSettings();
        settings.setFetchTimeoutMs(0); // disable timeout for deterministic tests
        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(settings);
        when(domain.getOidc()).thenReturn(oidc);
    }

    @Test
    void getKeys_returnsEmpty_whenTrustDomainNull() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);

        service.getKeys(null).test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkService, never()).getKeys(anyString());
    }

    @Test
    void getKeys_errorsForUnsupportedBundleSource() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        td.setBundleSource(SpiffeBundleSource.STATIC_JWKS);

        service.getKeys(td).test()
                .assertError(UnsupportedOperationException.class);
    }

    @Test
    void getKeys_returnsEmpty_whenJwksUrlBlank() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        td.setJwksUrl("");

        service.getKeys(td).test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkService, never()).getKeys(anyString());
    }

    @Test
    void getKeys_rejectsFetch_whenUrlResolvesToPrivateAddress() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        td.setJwksUrl("https://10.0.0.5/keys");

        service.getKeys(td).test()
                .assertError(SecurityException.class)
                .assertError(err -> err.getMessage().contains("Refused to fetch JWKS"));
        verify(jwkService, never()).getKeys(anyString());
    }

    @Test
    void getKeys_allowsPrivateAddress_whenDomainPolicyPermits() {
        settings.setAllowPrivateIpAddress(true);
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        td.setJwksUrl("https://10.0.0.5/keys");
        JWKSet bundle = jwks("kid-1");
        when(jwkService.getKeys("https://10.0.0.5/keys")).thenReturn(Maybe.just(bundle));

        service.getKeys(td).test().assertNoErrors().assertValue(bundle);
    }

    @Test
    void getKeys_rejectsHttp_unlessAllowedByPolicy() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        td.setJwksUrl("http://bundle.example.org/keys");

        service.getKeys(td).test()
                .assertError(SecurityException.class);
    }

    @Test
    void getKeys_cachesBundle_betweenCalls() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");
        when(jwkService.getKeys(JWKS_URL)).thenReturn(Maybe.just(bundle));

        service.getKeys(td).test().assertValue(bundle);
        service.getKeys(td).test().assertValue(bundle);

        verify(jwkService, times(1)).getKeys(JWKS_URL);
    }

    @Test
    void getKeys_servesStaleBundle_whenRefreshFails() throws InterruptedException {
        // Force soft-TTL = 0 so the second call always refreshes.
        settings.setCacheTtlSeconds(0);
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");

        when(jwkService.getKeys(JWKS_URL))
                .thenReturn(Maybe.just(bundle))
                .thenReturn(Maybe.error(new RuntimeException("upstream down")));

        // First call primes the cache.
        service.getKeys(td).test().assertValue(bundle);
        Thread.sleep(5);

        // Second call: refresh fails → stale bundle is returned.
        service.getKeys(td).test().assertNoErrors().assertValue(bundle);
        verify(jwkService, times(2)).getKeys(JWKS_URL);
    }

    @Test
    void getKeys_propagatesError_whenNoStaleAvailable() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        when(jwkService.getKeys(JWKS_URL)).thenReturn(Maybe.error(new RuntimeException("upstream down")));

        service.getKeys(td).test().assertError(RuntimeException.class);
    }

    @Test
    void getKey_returnsEmpty_forNullOrBlankKid() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();

        service.getKey(td, null).test().assertNoErrors().assertComplete().assertNoValues();
        service.getKey(td, "  ").test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkService, never()).getKeys(anyString());
    }

    @Test
    void getKey_returnsMatchingKey() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1", "kid-2");
        when(jwkService.getKeys(JWKS_URL)).thenReturn(Maybe.just(bundle));

        service.getKey(td, "kid-2").test()
                .assertNoErrors()
                .assertValue(k -> "kid-2".equals(k.getKid()));
    }

    @Test
    void getKey_refreshesOnMiss_andReturnsNewKid() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        JWKSet first = jwks("kid-1");
        JWKSet second = jwks("kid-1", "kid-2");

        when(jwkService.getKeys(JWKS_URL))
                .thenReturn(Maybe.just(first))
                .thenReturn(Maybe.just(second));

        // Prime cache with first bundle.
        service.getKeys(td).test().assertValue(first);

        // kid-2 miss should refresh → finds it in the new bundle.
        service.getKey(td, "kid-2").test()
                .assertNoErrors()
                .assertValue(k -> "kid-2".equals(k.getKid()));

        verify(jwkService, times(2)).getKeys(JWKS_URL);
    }

    @Test
    void evict_clearsCachedBundle() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");
        when(jwkService.getKeys(JWKS_URL)).thenReturn(Maybe.just(bundle));

        service.getKeys(td).test().assertValue(bundle);
        service.evict(td.getId());
        service.getKeys(td).test().assertValue(bundle);

        verify(jwkService, times(2)).getKeys(JWKS_URL);
    }

    @Test
    void evict_ignoresNullId() {
        TrustBundleServiceImpl service = new TrustBundleServiceImpl(jwkService, domain);
        // No throw is success here.
        service.evict(null);
    }

    // --- helpers -----------------------------------------------------------

    private static TrustDomain trustDomain() {
        TrustDomain td = TrustDomain.builder()
                .id("td-1")
                .name("example.org")
                .bundleSource(SpiffeBundleSource.JWKS_URL)
                .jwksUrl(JWKS_URL)
                .refreshIntervalSeconds(300)
                .build();
        return td;
    }

    private static JWKSet jwks(String... kids) {
        JWKSet set = new JWKSet();
        List<io.gravitee.am.model.jose.JWK> keys = new java.util.ArrayList<>();
        for (String kid : kids) {
            RSAKey k = new RSAKey();
            k.setKty("RSA");
            k.setKid(kid);
            keys.add(k);
        }
        set.setKeys(keys);
        return set;
    }
}
