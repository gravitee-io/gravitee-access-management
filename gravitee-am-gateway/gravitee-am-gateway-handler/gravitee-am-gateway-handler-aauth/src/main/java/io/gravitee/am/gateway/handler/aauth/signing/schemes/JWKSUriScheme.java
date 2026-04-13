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
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.service.JWKSDocument;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
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
 * JWKS URI scheme: resolves a public key by fetching the agent's metadata document
 * and JWKS, then looking up the key by {@code kid}.
 * <p>
 * Signature-Key header parameters:
 * <ul>
 *   <li><code>id</code> — agent server URL (the verified identity)</li>
 *   <li><code>kid</code> — key identifier to look up in the JWKS</li>
 *   <li><code>well-known</code> — metadata document name (e.g. "aauth-agent", without .json)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JWKSUriScheme implements SignatureScheme {

    private final AgentMetadataFetcher fetcher;

    @Override
    public ResolvedKey resolve(SignatureKeyInfo keyInfo) throws SignatureVerificationException {
        String agentId = keyInfo.getParam("id");    // Agent server URL
        String kid = keyInfo.getParam("kid");        // Key ID in the JWKS

        if (agentId == null || kid == null) {
            throw new SignatureVerificationException("invalid_key");
        }

        try {
            // 1. Fetch agent metadata
            AgentMetadata metadata = fetcher.fetchMetadata(agentId);
            if (metadata.jwksUri() == null) {
                throw new SignatureVerificationException("invalid_key");
            }

            // 2. Fetch JWKS and find key by kid
            JWKSDocument jwks = fetcher.fetchJWKS(metadata.jwksUri());
            JWK jwk = jwks.findByKid(kid);

            // 3. If not found, re-fetch once per spec requirement
            if (jwk == null) {
                log.debug("Key kid={} not found in JWKS, re-fetching from {}", kid, metadata.jwksUri());
                jwks = fetcher.fetchJWKS(metadata.jwksUri(), true);
                jwk = jwks.findByKid(kid);
            }

            if (jwk == null) {
                throw new SignatureVerificationException("unknown_key");
            }

            // 4. Convert JWK to PublicKey using Java native crypto
            return convertJwkToResolvedKey(jwk);
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve JWKS URI key (id={}, kid={}): {}", agentId, kid, e.getMessage());
            throw new SignatureVerificationException("invalid_key");
        }
    }

    private ResolvedKey convertJwkToResolvedKey(JWK jwk) throws Exception {
        if (jwk instanceof OctetKeyPair okp && Curve.Ed25519.equals(okp.getCurve())) {
            byte[] rawKey = okp.getDecodedX();
            PublicKey publicKey = rawBytesToEd25519PublicKey(rawKey);
            String thumbprint = computeThumbprint(jwk);
            return new ResolvedKey(publicKey, "Ed25519", thumbprint);
        } else if (jwk instanceof ECKey ecKey && Curve.P_256.equals(ecKey.getCurve())) {
            byte[] xBytes = ecKey.getX().decode();
            byte[] yBytes = ecKey.getY().decode();
            ECPoint point = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
            ECParameterSpec ecSpec = getP256Spec();
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, ecSpec));
            String thumbprint = computeThumbprint(jwk);
            return new ResolvedKey(publicKey, "SHA256withECDSA", thumbprint);
        } else {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", "EdDSA, ES256"));
        }
    }

    private PublicKey rawBytesToEd25519PublicKey(byte[] raw) throws Exception {
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

    private String computeThumbprint(JWK jwk) throws Exception {
        // RFC 7638 — use Nimbus for canonical JSON, then SHA-256
        return jwk.computeThumbprint().toString();
    }

    private ECParameterSpec getP256Spec() throws Exception {
        var kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var tmpKey = (java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic();
        return tmpKey.getParams();
    }
}
