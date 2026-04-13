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
package io.gravitee.am.gateway.handler.aauth.signing.schemes;

import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.*;

public class HeaderWebKeySchemeTest {

    private final HeaderWebKeyScheme scheme = new HeaderWebKeyScheme();

    @Test
    public void shouldResolveEd25519PublicKey() throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();

        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "hwk",
                Map.of("kty", "OKP", "crv", "Ed25519", "x", x));

        ResolvedKey resolved = scheme.resolve(keyInfo);

        assertNotNull(resolved.publicKey());
        assertEquals("Ed25519", resolved.algorithm());
        assertNotNull(resolved.jwkThumbprint());
        assertFalse(resolved.jwkThumbprint().isEmpty());
    }

    @Test
    public void shouldResolveP256PublicKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = kpg.generateKeyPair();
        ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();

        byte[] xBytes = ecPub.getW().getAffineX().toByteArray();
        byte[] yBytes = ecPub.getW().getAffineY().toByteArray();
        // Ensure 32 bytes (strip leading zero if present)
        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toFixedLength(xBytes, 32));
        String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toFixedLength(yBytes, 32));

        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "hwk",
                Map.of("kty", "EC", "crv", "P-256", "x", x, "y", y));

        ResolvedKey resolved = scheme.resolve(keyInfo);

        assertNotNull(resolved.publicKey());
        assertEquals("SHA256withECDSA", resolved.algorithm());
        assertNotNull(resolved.jwkThumbprint());
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectUnsupportedCurve() throws Exception {
        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "hwk",
                Map.of("kty", "OKP", "crv", "Ed448", "x", "dGVzdA"));

        scheme.resolve(keyInfo);
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectMissingKty() throws Exception {
        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "hwk",
                Map.of("crv", "Ed25519", "x", "dGVzdA"));

        scheme.resolve(keyInfo);
    }

    @Test
    public void shouldComputeJwkThumbprint() throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();

        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "hwk",
                Map.of("kty", "OKP", "crv", "Ed25519", "x", x));

        ResolvedKey resolved = scheme.resolve(keyInfo);

        // Thumbprint should be a base64url-encoded SHA-256 hash (43 chars without padding)
        assertTrue("Thumbprint should be ~43 chars", resolved.jwkThumbprint().length() >= 40);
        // Should be deterministic
        ResolvedKey resolved2 = scheme.resolve(keyInfo);
        assertEquals(resolved.jwkThumbprint(), resolved2.jwkThumbprint());
    }

    private byte[] toFixedLength(byte[] input, int length) {
        if (input.length == length) return input;
        byte[] result = new byte[length];
        if (input.length > length) {
            System.arraycopy(input, input.length - length, result, 0, length);
        } else {
            System.arraycopy(input, 0, result, length - input.length, input.length);
        }
        return result;
    }
}
