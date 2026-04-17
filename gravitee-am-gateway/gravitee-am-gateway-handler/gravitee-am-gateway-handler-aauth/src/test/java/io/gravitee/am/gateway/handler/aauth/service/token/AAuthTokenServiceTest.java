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
package io.gravitee.am.gateway.handler.aauth.service.token;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.jwt.JWTBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AAuthTokenService}.
 */
public class AAuthTokenServiceTest {

    private CertificateManager certificateManager;
    private JWTBuilder jwtBuilder;
    private AAuthTokenService tokenService;
    private KeyPair agentKeyPair;

    @Before
    public void setUp() {
        certificateManager = mock(CertificateManager.class);
        tokenService = new AAuthTokenService(certificateManager, 300);
        agentKeyPair = TestAgentKeyPairFactory.ed25519();

        CertificateProvider certProvider = mock(CertificateProvider.class);
        jwtBuilder = mock(JWTBuilder.class);
        when(certificateManager.defaultCertificateProvider()).thenReturn(certProvider);
        when(certificateManager.findByAlgorithm(anyString()))
                .thenReturn(io.reactivex.rxjava3.core.Maybe.just(certProvider));
        when(certProvider.getJwtBuilder()).thenReturn(jwtBuilder);
        when(jwtBuilder.sign(any(JWT.class), eq("aa-auth+jwt"))).thenReturn("signed.jwt.token");
    }

    @Test
    public void shouldCreateAuthToken_m2m() {
        long now = Instant.now().getEpochSecond();
        ResourceTokenClaims rtClaims = new ResourceTokenClaims(
                "https://resource.example", "https://ps.example/aauth",
                "jti-123", "aauth:bot@agent.example", "thumbprint", "read write",
                now, now + 300);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), "thumbprint", "https://agent.example", "aauth:bot@agent.example");

        var result = tokenService.createAuthToken(rtClaims, verification, "https://ps.example/aauth")
                .blockingGet();

        assertNotNull(result);
        assertEquals("signed.jwt.token", result.authToken());
        assertEquals(300L, result.expiresIn());

        // Verify JWT claims and that typ=aa-auth+jwt was requested
        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-auth+jwt"));

        JWT jwt = jwtCaptor.getValue();
        assertEquals("https://ps.example/aauth", jwt.getIss());
        assertEquals("aauth-person.json", jwt.get("dwk"));
        assertEquals("https://resource.example", jwt.getAud());
        assertEquals("aauth:bot@agent.example", jwt.get("agent"));
        assertEquals("read write", jwt.get("scope"));
        assertNotNull(jwt.getJti());
        assertNotNull(jwt.get("cnf"));
        assertNotNull(jwt.get("act"));
        assertNull(jwt.getSub()); // m2m, no sub

        @SuppressWarnings("unchecked")
        Map<String, Object> act = (Map<String, Object>) jwt.get("act");
        assertEquals("aauth:bot@agent.example", act.get("sub"));
    }

    @Test
    public void shouldCreateAuthToken_withSub() {
        long now = Instant.now().getEpochSecond();
        ResourceTokenClaims rtClaims = new ResourceTokenClaims(
                "https://resource.example", "https://ps.example/aauth",
                "jti-456", "aauth:bot@agent.example", "thumbprint", "read",
                now, now + 300);

        VerificationResult verification = new VerificationResult(
                "jwks_uri", "sig", agentKeyPair.getPublic(), "thumbprint", "https://agent.example", "aauth:bot@agent.example");

        var result = tokenService.createAuthToken(rtClaims, verification, "https://ps.example/aauth", "user-123")
                .blockingGet();

        assertNotNull(result);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-auth+jwt"));

        JWT jwt = jwtCaptor.getValue();
        assertEquals("user-123", jwt.getSub());
        assertEquals("read", jwt.get("scope"));
    }

    @Test
    public void shouldSetCnfJwkClaim() {
        long now = Instant.now().getEpochSecond();
        ResourceTokenClaims rtClaims = new ResourceTokenClaims(
                "https://resource.example", "https://ps.example/aauth",
                "jti-789", "aauth:bot@agent.example", "thumbprint", "read",
                now, now + 300);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), "thumbprint", "https://agent.example", "aauth:bot@agent.example");

        tokenService.createAuthToken(rtClaims, verification, "https://ps.example/aauth").blockingGet();

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtBuilder).sign(jwtCaptor.capture(), eq("aa-auth+jwt"));

        JWT jwt = jwtCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> cnf = (Map<String, Object>) jwt.get("cnf");
        assertNotNull("cnf claim should be present", cnf);

        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = (Map<String, Object>) cnf.get("jwk");
        assertNotNull("cnf.jwk should be present", jwk);
        assertEquals("OKP", jwk.get("kty"));
        assertEquals("Ed25519", jwk.get("crv"));
        assertNotNull("x coordinate should be present", jwk.get("x"));
    }
}
