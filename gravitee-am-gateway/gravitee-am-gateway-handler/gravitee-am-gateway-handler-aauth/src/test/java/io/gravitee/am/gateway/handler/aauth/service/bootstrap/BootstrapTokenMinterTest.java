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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.jwt.JWTBuilder;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BootstrapTokenMinterTest {

    private static final String EPHEMERAL_JWK =
            "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"abc123\"}";

    private CertificateManager certificateManager;
    private CertificateProvider certProvider;
    private JWTBuilder jwtBuilder;

    @Before
    public void setUp() {
        certificateManager = mock(CertificateManager.class);
        certProvider = mock(CertificateProvider.class);
        jwtBuilder = mock(JWTBuilder.class);
        when(certProvider.getJwtBuilder()).thenReturn(jwtBuilder);
        when(jwtBuilder.sign(any(JWT.class), eq("aa-bootstrap+jwt"))).thenReturn("signed.jwt.token");
        // Default: both algorithm lookups return empty so each test can selectively
        // stub the algorithm(s) it cares about. Without this, any unstubbed method on
        // the mock returns null, and `Maybe.switchIfEmpty(null)` NPEs.
        when(certificateManager.findByAlgorithm(anyString())).thenReturn(Maybe.empty());
    }

    @Test
    public void shouldMintWithRs256Certificate() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));
        when(certificateManager.findByAlgorithm("ES256")).thenReturn(Maybe.empty());

        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 300);
        String token = minter.mint("https://ps.example", "https://agent.example",
                "pairwise-sub", EPHEMERAL_JWK).blockingGet();

        assertEquals("signed.jwt.token", token);
    }

    @Test
    public void shouldFallBackToEs256WhenRs256Absent() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.empty());
        when(certificateManager.findByAlgorithm("ES256")).thenReturn(Maybe.just(certProvider));

        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 300);
        String token = minter.mint("https://ps.example", "https://agent.example",
                "pairwise-sub", EPHEMERAL_JWK).blockingGet();

        assertEquals("signed.jwt.token", token);
    }

    @Test
    public void shouldFailFastWhenNoAsymmetricCertificateAvailable() {
        // Critical security property: never fall back to a symmetric / HMAC default.
        // The bootstrap_token must be verifiable by the AS through the PS JWKS, which only
        // publishes asymmetric keys. A symmetric-signed token would be silently unverifiable.
        when(certificateManager.findByAlgorithm(anyString())).thenReturn(Maybe.empty());

        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 300);

        minter.mint("https://ps.example", "https://agent.example", "pairwise-sub", EPHEMERAL_JWK)
                .test()
                .assertError(BootstrapTokenSigningException.class);
    }

    @Test
    public void shouldRejectMalformedEphemeralJwk() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));

        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 300);

        minter.mint("https://ps.example", "https://agent.example", "pairwise-sub", "{not json")
                .test()
                .assertError(IllegalArgumentException.class);
    }

    @Test
    public void shouldClampLifespanAboveSpecMaximum() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));
        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 999);

        long now = java.time.Instant.now().getEpochSecond();
        minter.mint("https://ps.example", "https://agent.example", "pairwise-sub", EPHEMERAL_JWK)
                .blockingGet();

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        org.mockito.Mockito.verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-bootstrap+jwt"));
        JWT signed = jwtCaptor.getValue();
        long exp = ((Number) signed.get("exp")).longValue();
        long iat = signed.getIat();
        assertEquals("lifespan must be capped at spec maximum 300s",
                BootstrapTokenMinter.MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS, exp - iat);
        assertTrue("iat should be near 'now'", Math.abs(iat - now) < 5);
    }

    @Test
    public void shouldClampLifespanBelowPracticalMinimum() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));
        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 5);

        minter.mint("https://ps.example", "https://agent.example", "pairwise-sub", EPHEMERAL_JWK)
                .blockingGet();

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        org.mockito.Mockito.verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-bootstrap+jwt"));
        JWT signed = jwtCaptor.getValue();
        long exp = ((Number) signed.get("exp")).longValue();
        long iat = signed.getIat();
        assertEquals("lifespan must be raised to practical minimum 30s",
                BootstrapTokenMinter.MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS, exp - iat);
    }

    @Test
    public void shouldHonorOperatorChosenLifespanInRange() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));
        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 120);

        minter.mint("https://ps.example", "https://agent.example", "pairwise-sub", EPHEMERAL_JWK)
                .blockingGet();

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        org.mockito.Mockito.verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-bootstrap+jwt"));
        JWT signed = jwtCaptor.getValue();
        long exp = ((Number) signed.get("exp")).longValue();
        long iat = signed.getIat();
        assertEquals(120, exp - iat);
    }

    @Test
    public void shouldSetCoreClaims() {
        when(certificateManager.findByAlgorithm("RS256")).thenReturn(Maybe.just(certProvider));
        BootstrapTokenMinter minter = new BootstrapTokenMinter(certificateManager, 300);

        minter.mint("https://ps.example/aauth", "https://agent.example",
                "pairwise-sub-xyz", EPHEMERAL_JWK).blockingGet();

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        org.mockito.Mockito.verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-bootstrap+jwt"));
        JWT signed = jwtCaptor.getValue();

        assertEquals("https://ps.example/aauth", signed.getIss());
        assertEquals("aauth-person.json", signed.get("dwk"));
        assertEquals("https://agent.example", signed.getAud());
        assertEquals("pairwise-sub-xyz", signed.getSub());
        assertTrue("jti must be set and non-blank",
                signed.getJti() != null && !signed.getJti().isBlank());
        assertTrue("cnf must contain jwk", signed.get("cnf") instanceof java.util.Map);
    }
}
