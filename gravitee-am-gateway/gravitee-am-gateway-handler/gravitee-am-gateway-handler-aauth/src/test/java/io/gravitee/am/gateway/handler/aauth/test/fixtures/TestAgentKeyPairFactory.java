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
package io.gravitee.am.gateway.handler.aauth.test.fixtures;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.util.Base64;

/**
 * Generates keypairs for AAUTH tests using Java native crypto.
 * Uses Nimbus JOSE for the public key x-coordinate extraction (to ensure compatibility
 * with the HWK scheme parser which uses Nimbus).
 */
public final class TestAgentKeyPairFactory {

    private static KeyPair ed25519KeyPair;
    private static String ed25519X; // cached base64url x-coordinate

    private TestAgentKeyPairFactory() {
    }

    public static synchronized KeyPair ed25519() {
        if (ed25519KeyPair == null) {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
                ed25519KeyPair = kpg.generateKeyPair();
                // Pre-compute the x coordinate via Nimbus for consistency
                ed25519X = computeEd25519X(ed25519KeyPair);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate Ed25519 keypair", e);
            }
        }
        return ed25519KeyPair;
    }

    /**
     * Get the Ed25519 public key x-coordinate as base64url, compatible with Nimbus JOSE parsing.
     */
    public static String ed25519PublicKeyX() {
        ed25519(); // ensure initialized
        return ed25519X;
    }

    private static String computeEd25519X(KeyPair keyPair) throws Exception {
        EdECPublicKey pubKey = (EdECPublicKey) keyPair.getPublic();
        EdECPoint point = pubKey.getPoint();

        // Convert EdECPoint to the raw 32-byte Ed25519 encoding per RFC 8032
        byte[] yBytes = point.getY().toByteArray();

        // Ed25519 uses little-endian 32-byte encoding with the high bit of last byte = x-sign
        byte[] raw = new byte[32];
        // yBytes is big-endian; reverse into raw (little-endian)
        for (int i = 0; i < Math.min(yBytes.length, 32); i++) {
            raw[i] = yBytes[yBytes.length - 1 - i];
        }
        // Set the x-sign bit
        if (point.isXOdd()) {
            raw[31] |= (byte) 0x80;
        }

        // Build a Nimbus OctetKeyPair to get the canonical x-coordinate
        OctetKeyPair okp = OctetKeyPair.parse(
                "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(raw) + "\"}"
        );
        return okp.getX().toString();
    }
}
