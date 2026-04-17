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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.junit.Assert.*;

/**
 * Tests for {@link JwkKeyConverter}.
 */
public class JwkKeyConverterTest {

    @Test
    public void shouldConvertEd25519JwkToPublicKey() throws Exception {
        var kp = TestAgentKeyPairFactory.ed25519();
        byte[] raw = new byte[32];
        byte[] encoded = kp.getPublic().getEncoded();
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);

        var jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(raw)).build();

        var publicKey = JwkKeyConverter.toNativePublicKey(jwk);
        assertNotNull(publicKey);
        assertEquals("EdDSA", publicKey.getAlgorithm());
        assertEquals("Ed25519", JwkKeyConverter.algorithmForJwk(jwk));
    }

    @Test
    public void shouldConvertP256JwkToPublicKey() throws Exception {
        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        var kp = kpg.generateKeyPair();
        var ecPub = (ECPublicKey) kp.getPublic();

        var jwk = new ECKey.Builder(Curve.P_256, ecPub).build();

        var publicKey = JwkKeyConverter.toNativePublicKey(jwk);
        assertNotNull(publicKey);
        assertEquals("EC", publicKey.getAlgorithm());
        assertEquals("SHA256withECDSA", JwkKeyConverter.algorithmForJwk(jwk));
    }

    @Test
    public void shouldComputeRfc7638Thumbprint() throws Exception {
        var kp = TestAgentKeyPairFactory.ed25519();
        byte[] raw = new byte[32];
        byte[] encoded = kp.getPublic().getEncoded();
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);

        var jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(raw)).build();

        String thumbprint = JwkKeyConverter.computeThumbprint(jwk);
        assertNotNull(thumbprint);
        assertFalse(thumbprint.isEmpty());
        // Thumbprint should be deterministic
        assertEquals(thumbprint, JwkKeyConverter.computeThumbprint(jwk));
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectUnsupportedCurve() throws Exception {
        // Build a P-384 key (unsupported)
        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp384r1"));
        var kp = kpg.generateKeyPair();
        var ecPub = (ECPublicKey) kp.getPublic();

        var jwk = new ECKey.Builder(Curve.P_384, ecPub).build();
        JwkKeyConverter.toNativePublicKey(jwk);
    }
}
