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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Map;

/**
 * Shared utility for converting Nimbus JWK objects to Java native PublicKeys.
 * Used by JWKSUriScheme and JWTScheme to avoid Nimbus {@code toPublicKey()} which
 * fails on Java 25+ without Bouncy Castle.
 *
 * @author GraviteeSource Team
 */
public final class JwkKeyConverter {

    private JwkKeyConverter() {}

    /**
     * Convert a Nimbus JWK to a Java native PublicKey.
     *
     * @throws SignatureVerificationException if the key type or curve is unsupported
     */
    public static PublicKey toNativePublicKey(JWK jwk) throws Exception {
        if (jwk instanceof RSAKey rsaKey) {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new java.security.spec.RSAPublicKeySpec(
                            rsaKey.getModulus().decodeToBigInteger(),
                            rsaKey.getPublicExponent().decodeToBigInteger()));
        } else if (jwk instanceof OctetKeyPair okp && Curve.Ed25519.equals(okp.getCurve())) {
            return rawBytesToEd25519PublicKey(okp.getDecodedX());
        } else if (jwk instanceof ECKey ecKey && Curve.P_256.equals(ecKey.getCurve())) {
            byte[] xBytes = ecKey.getX().decode();
            byte[] yBytes = ecKey.getY().decode();
            ECPoint point = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
            ECParameterSpec ecSpec = getP256Spec();
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, ecSpec));
        } else {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", "RS256, RS384, RS512, EdDSA, ES256"));
        }
    }

    /**
     * Return the Java algorithm name for signature verification.
     */
    public static String algorithmForJwk(JWK jwk) {
        if (jwk instanceof RSAKey) {
            return "SHA256withRSA";
        } else if (jwk instanceof OctetKeyPair okp && Curve.Ed25519.equals(okp.getCurve())) {
            return "Ed25519";
        } else if (jwk instanceof ECKey ecKey && Curve.P_256.equals(ecKey.getCurve())) {
            return "SHA256withECDSA";
        }
        return null;
    }

    /**
     * Compute RFC 7638 JWK Thumbprint (base64url-encoded SHA-256).
     */
    public static String computeThumbprint(JWK jwk) throws Exception {
        return jwk.computeThumbprint().toString();
    }

    /**
     * Convert a raw 32-byte Ed25519 public key to a Java PublicKey.
     */
    public static PublicKey rawBytesToEd25519PublicKey(byte[] raw) throws Exception {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be 32 bytes, got " + raw.length);
        }
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yLE = raw.clone();
        yLE[31] &= 0x7F;
        byte[] yBE = new byte[yLE.length];
        for (int i = 0; i < yLE.length; i++) {
            yBE[i] = yLE[yLE.length - 1 - i];
        }
        BigInteger y = new BigInteger(1, yBE);
        EdECPoint point = new EdECPoint(xOdd, y);
        return KeyFactory.getInstance("Ed25519").generatePublic(
                new EdECPublicKeySpec(NamedParameterSpec.ED25519, point));
    }

    private static ECParameterSpec getP256Spec() throws Exception {
        var kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        var tmpKey = (java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic();
        return tmpKey.getParams();
    }
}
