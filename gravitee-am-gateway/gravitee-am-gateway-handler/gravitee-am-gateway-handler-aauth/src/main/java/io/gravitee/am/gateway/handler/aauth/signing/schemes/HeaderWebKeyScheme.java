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
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;
import java.util.Map;

/**
 * HWK (Header Web Key) scheme: resolves a public key from inline JWK parameters
 * carried directly in the Signature-Key header.
 * <p>
 * Uses Java native crypto (no Nimbus toPublicKey() — which fails on Java 25 without Bouncy Castle).
 * <p>
 * Supports: OKP/Ed25519 (EdDSA), EC/P-256 (ECDSA with SHA-256).
 */
@Slf4j
public class HeaderWebKeyScheme implements SignatureScheme {

    @Override
    public ResolvedKey resolve(SignatureKeyInfo keyInfo) throws SignatureVerificationException {
        String kty = keyInfo.getParam("kty");  // Key Type: "OKP" (Edwards-curve), "EC" (Elliptic Curve)
        String crv = keyInfo.getParam("crv");  // Curve: "Ed25519", "P-256"

        if (kty == null || crv == null) {
            throw new SignatureVerificationException("invalid_key");
        }

        try {
            return switch (kty) {
                case "OKP" -> resolveOkp(keyInfo, crv);
                case "EC" -> resolveEc(keyInfo, crv);
                default -> throw new SignatureVerificationException("unsupported_algorithm",
                        Map.of("supported_algorithms", "EdDSA, ES256"));
            };
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve HWK key (kty={}, crv={}): {}", kty, crv, e.getMessage(), e);
            throw new SignatureVerificationException("invalid_key");
        }
    }

    // OKP = Octet Key Pair (Edwards-curve keys per RFC 8037)
    private ResolvedKey resolveOkp(SignatureKeyInfo keyInfo, String crv) throws Exception {
        if (!"Ed25519".equals(crv)) {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", "EdDSA, ES256"));
        }

        String x = keyInfo.getParam("x");  // x = public key coordinate (base64url-encoded, 32 bytes for Ed25519)
        if (x == null) {
            throw new SignatureVerificationException("invalid_key");
        }

        byte[] rawKey = Base64.getUrlDecoder().decode(x);
        PublicKey publicKey = rawBytesToEd25519PublicKey(rawKey);
        String thumbprint = computeEd25519Thumbprint(x);

        return new ResolvedKey(publicKey, "Ed25519", thumbprint);
    }

    // EC = Elliptic Curve (ECDSA keys per RFC 7518)
    private ResolvedKey resolveEc(SignatureKeyInfo keyInfo, String crv) throws Exception {
        if (!"P-256".equals(crv)) {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", "EdDSA, ES256"));
        }

        String x = keyInfo.getParam("x");
        String y = keyInfo.getParam("y");
        if (x == null || y == null) {
            throw new SignatureVerificationException("invalid_key");
        }

        byte[] xBytes = Base64.getUrlDecoder().decode(x);
        byte[] yBytes = Base64.getUrlDecoder().decode(y);

        ECPoint point = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
        ECParameterSpec ecSpec = getP256Spec();
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecSpec);
        PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(keySpec);
        String thumbprint = computeEcThumbprint(crv, x, y);

        return new ResolvedKey(publicKey, "SHA256withECDSA", thumbprint);
    }

    /**
     * Convert raw Ed25519 public key bytes (32 bytes, RFC 8032 encoding) to a Java PublicKey.
     * The raw encoding is: y-coordinate in little-endian with the x-sign bit in the high bit of byte 31.
     */
    private PublicKey rawBytesToEd25519PublicKey(byte[] raw) throws Exception {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be 32 bytes, got " + raw.length);
        }

        // Extract x-sign from high bit of last byte
        boolean xOdd = (raw[31] & 0x80) != 0;

        // Clear the x-sign bit to get the y-coordinate
        byte[] yLE = raw.clone();
        yLE[31] &= 0x7F;

        // Convert little-endian to BigInteger (big-endian)
        byte[] yBE = new byte[yLE.length];
        for (int i = 0; i < yLE.length; i++) {
            yBE[i] = yLE[yLE.length - 1 - i];
        }
        BigInteger y = new BigInteger(1, yBE);

        EdECPoint point = new EdECPoint(xOdd, y);
        EdECPublicKeySpec keySpec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
        return KeyFactory.getInstance("Ed25519").generatePublic(keySpec);
    }

    /**
     * Compute RFC 7638 JWK Thumbprint for an Ed25519 key.
     * Canonical JSON: {"crv":"Ed25519","kty":"OKP","x":"<base64url>"}
     */
    /**
     * Compute RFC 7638 JWK Thumbprint for an OKP/Ed25519 key.
     * Per RFC 7638, the canonical JSON uses alphabetically sorted required members.
     */
    private String computeEd25519Thumbprint(String x) throws Exception {
        // crv (Curve), kty (Key Type), x (public key x-coordinate) — alphabetical order
        var canonicalJson = """
                {"crv":"Ed25519","kty":"OKP","x":"%s"}""".formatted(x);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Compute RFC 7638 JWK Thumbprint for an EC key.
     * Per RFC 7638, the canonical JSON uses alphabetically sorted required members.
     */
    private String computeEcThumbprint(String crv, String x, String y) throws Exception {
        // crv (Curve), kty (Key Type), x (x-coordinate), y (y-coordinate) — alphabetical order
        var canonicalJson = """
                {"crv":"%s","kty":"EC","x":"%s","y":"%s"}""".formatted(crv, x, y);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private ECParameterSpec getP256Spec() throws Exception {
        var kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var tmpKey = (java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic();
        return tmpKey.getParams();
    }
}
