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
package io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl;

import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustDomainKeyServiceImplTest {

    // example.com is an IANA-reserved real domain that always resolves; we want the
    // URL safety check to pass so the test can exercise the fetch/cache logic.
    /** Self-signed throwaway certificate holding a single RSA key. */
    private static final String PEM_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDGzCCAgOgAwIBAgIUTjn3isOFd6UKhH07F2M9E0+1naMwDQYJKoZIhvcNAQEL
            BQAwHDEaMBgGA1UEAwwRdHJ1c3QtZG9tYWluLXRlc3QwIBcNMjYwODE5MDk0NTU3
            WhgPMjEyNjA3MjYwOTQ1NTdaMBwxGjAYBgNVBAMMEXRydXN0LWRvbWFpbi10ZXN0
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1R3nx/2UCXBoJKsq2K3Z
            POGsH8Djxj2nnSZ081MVRQL3YgvrSmgzmRSwr6sYx9oJ8Kn32IINUi/aLiwzxlFL
            ACK3D/vjWmjP+kwIep64LCzHdD7PaP0ePgN/0jme1pOWVQgyHVs+0q0w6vepHpKu
            l9NcAkMPd7Pq0fUTPg9ebW2sGko7EKZvcsE3/nOA7fStycEtmscLr38wZFGIZSIw
            6QJ8Eww9Rn7CFhTXnE88/LGJ104YQ9taDR7uDQdB42dss2YQ/tWF82LL/PS/9zFW
            zdlF11dI1Xyo3LTTt584PQkDXp0B+wrT4YoOskTK2aMO1IIkV/iNMUDs6fzbtJvY
            AQIDAQABo1MwUTAdBgNVHQ4EFgQUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswHwYDVR0j
            BBgwFoAUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswDwYDVR0TAQH/BAUwAwEB/zANBgkq
            hkiG9w0BAQsFAAOCAQEAn84I8ZiCavT+RBk4f0eHd5pnT9Scj0zcEElwgDw+8nNM
            eyTSuPdngL9pQCSBbZbo7ZNCe0WFOf2W/SLIzRx6E3+z1ffAmLr9/LTC1+UNuh1n
            feEljw97Q0sJKd/EyyUg+ZwUwQ5yiTT8hAQdai1kWhRLbalI2cYYJ+1HlXD73eKM
            9WrINYHmLrXclebEtB3SPTTeGtMZ/ajKlcBV1fu/+OoN5e09hvvEEJZ48aNYZrTt
            2An+LQABjo4LNsjP1prK4PtQl1/PI/jHUiRkHfWZx3y2Rakw5bu+bWMMn3vL4Dsk
            rXiZ9rmdTvbQ38TVI3+G5aBLVP9DS+9cXdHfSlyVPg==
            -----END CERTIFICATE-----""";

    private static final String JWKS_URL = "https://example.com/keys";

    private static final long DEFAULT_MAX_RESPONSE_SIZE_BYTES =
            (long) KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB * 1024L;

    @Mock
    private JWKSetFetcher jwkSetFetcher;

    private final Domain domain = new Domain();

    private KeyRetrievalSettings settings;

    @BeforeEach
    void setUp() {
        settings = new KeyRetrievalSettings();
        settings.setFetchTimeoutMs(0); // disable timeout for deterministic tests
        domain.setKeyRetrievalSettings(settings);
    }

    @Test
    void getKeys_returnsEmpty_whenTrustDomainNull() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);

        service.getKeys(null).test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void getKeys_returnsEmpty_whenKeyMaterialMissing() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.setKeyMaterial(null);

        service.getKeys(td).test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldReturnInlineJwkSet_withoutFetching() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        JWKSet inline = jwks("kid-1");
        TrustDomain td = trustDomain();
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(inline)
                .build());

        service.getKeys(td).test().assertNoErrors().assertValue(inline);
        service.getKey(td, "kid-1").test().assertNoErrors().assertValue(key -> "kid-1".equals(key.getKid()));
        service.getKey(td, "unknown-kid").test().assertNoErrors().assertNoValues();
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldReturnPemKeyRegardlessOfKid() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate(PEM_CERTIFICATE)
                .build());

        service.getKeys(td).test().assertNoErrors().assertValue(set -> set.getKeys().size() == 1);
        service.getKey(td, "any-kid").test().assertNoErrors()
                .assertValue(key -> "RSA".equals(key.getKty()));
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldErrorOnUnparseablePemCertificate() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate("not-a-certificate")
                .build());

        service.getKeys(td).test().assertError(IllegalStateException.class);
    }

    @Test
    void getKeys_returnsEmpty_whenJwksUrlBlank() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.getKeyMaterial().setJwksUrl("");

        service.getKeys(td).test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void getKeys_rejectsFetch_whenUrlResolvesToPrivateAddress() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.getKeyMaterial().setJwksUrl("https://10.0.0.5/keys");

        service.getKeys(td).test()
                .assertError(SecurityException.class)
                .assertError(err -> err.getMessage().contains("Refused to fetch JWKS"));
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void getKeys_allowsPrivateAddress_whenDomainPolicyPermits() {
        settings.setAllowPrivateIpAddress(true);
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.getKeyMaterial().setJwksUrl("https://10.0.0.5/keys");
        JWKSet bundle = jwks("kid-1");
        when(jwkSetFetcher.getKeys("https://10.0.0.5/keys", DEFAULT_MAX_RESPONSE_SIZE_BYTES)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKeys(td).test().assertNoErrors().assertValue(bundle);
    }

    @Test
    void shouldHonorLimitsHeldBySpiffeBlockBeforeRelocation() {
        OIDCSettings legacyOidc = new OIDCSettings();
        SpiffeDomainSettings legacy = new SpiffeDomainSettings();
        legacy.setFetchTimeoutMs(0);
        legacy.setMaxResponseSizeKb(8);
        legacy.setAllowPrivateIpAddress(true);
        legacyOidc.setWorkloadIdentitySettings(legacy);
        Domain legacyDomain = new Domain();
        legacyDomain.setOidc(legacyOidc);
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, legacyDomain);
        TrustDomain td = trustDomain();
        td.getKeyMaterial().setJwksUrl("https://10.0.0.5/keys");
        JWKSet bundle = jwks("kid-1");
        when(jwkSetFetcher.getKeys("https://10.0.0.5/keys", 8 * 1024L)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKeys(td).test().assertNoErrors().assertValue(bundle);
    }

    @Test
    void getKeys_rejectsHttp_unlessAllowedByPolicy() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        td.getKeyMaterial().setJwksUrl("http://bundle.example.org/keys");

        service.getKeys(td).test()
                .assertError(SecurityException.class);
    }

    @Test
    void getKeys_cachesBundle_betweenCalls() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKeys(td).test().assertValue(bundle);
        service.getKeys(td).test().assertValue(bundle);

        verify(jwkSetFetcher, times(1)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void getKeys_servesStaleBundle_whenRefreshFails() {
        // Force soft-TTL = 0 so the second call always refreshes.
        settings.setCacheTtlSeconds(0);
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");

        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)))
                .thenReturn(Maybe.error(new RuntimeException("upstream down")));

        // First call primes the cache.
        service.getKeys(td).test().assertValue(bundle);

        // Second call: refresh fails → stale bundle is returned.
        service.getKeys(td).test().assertNoErrors().assertValue(bundle);
        verify(jwkSetFetcher, times(2)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void getKeys_propagatesError_whenNoStaleAvailable() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES)).thenReturn(Maybe.error(new RuntimeException("upstream down")));

        service.getKeys(td).test().assertError(RuntimeException.class);
    }

    @Test
    void getKey_returnsEmpty_forNullOrBlankKid() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();

        service.getKey(td, null).test().assertNoErrors().assertComplete().assertNoValues();
        service.getKey(td, "  ").test().assertNoErrors().assertComplete().assertNoValues();
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void getKey_returnsMatchingKey() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1", "kid-2");
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKey(td, "kid-2").test()
                .assertNoErrors()
                .assertValue(k -> "kid-2".equals(k.getKid()));
    }

    @Test
    void getKey_refreshesOnMiss_andReturnsNewKid() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet first = jwks("kid-1");
        JWKSet second = jwks("kid-1", "kid-2");

        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(first, null)))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(second, null)));

        // Prime cache with first bundle.
        service.getKeys(td).test().assertValue(first);

        // kid-2 miss should refresh → finds it in the new bundle.
        service.getKey(td, "kid-2").test()
                .assertNoErrors()
                .assertValue(k -> "kid-2".equals(k.getKid()));

        verify(jwkSetFetcher, times(2)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void shouldBoundResponseSizeWithConfiguredMaximum() {
        settings.setMaxResponseSizeKb(8);
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");
        when(jwkSetFetcher.getKeys(JWKS_URL, 8 * 1024L)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKeys(td).test().assertNoErrors().assertValue(bundle);

        verify(jwkSetFetcher, times(1)).getKeys(JWKS_URL, 8 * 1024L);
    }

    @Test
    void evict_clearsCachedBundle() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        TrustDomain td = trustDomain();
        JWKSet bundle = jwks("kid-1");
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES)).thenReturn(Maybe.just(new JWKSetFetchResponse(bundle, null)));

        service.getKeys(td).test().assertValue(bundle);
        service.evict(td.getId());
        service.getKeys(td).test().assertValue(bundle);

        verify(jwkSetFetcher, times(2)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void evict_ignoresNullId() {
        TrustDomainKeyServiceImpl service = new TrustDomainKeyServiceImpl(jwkSetFetcher, domain);
        // No throw is success here.
        service.evict(null);
    }

    // --- helpers -----------------------------------------------------------

    private static TrustDomain trustDomain() {
        TrustDomain td = TrustDomain.builder()
                .id("td-1")
                .name("example.org")
                .keyMaterial(TrustDomainKeyMaterial.builder()
                        .source(KeyMaterialSource.JWKS_URL)
                        .jwksUrl(JWKS_URL)
                        .build())
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
