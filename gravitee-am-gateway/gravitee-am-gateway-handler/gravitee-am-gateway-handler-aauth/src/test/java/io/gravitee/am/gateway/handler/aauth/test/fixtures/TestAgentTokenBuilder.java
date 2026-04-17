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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Builds signed {@code aa-agent+jwt} tokens for testing.
 */
public class TestAgentTokenBuilder {

    /**
     * Build an aa-agent+jwt token signed by the issuer's Ed25519 key.
     *
     * @param issuerKeyPair  the agent server's signing key pair (Ed25519)
     * @param iss            the agent server URL (issuer)
     * @param sub            the agent identity (subject)
     * @param delegateKey    the delegate's public key to embed in cnf.jwk
     * @param ttlSeconds     token lifetime
     * @return compact JWT string
     */
    public static String buildAgentToken(KeyPair issuerKeyPair, String iss, String sub,
                                          PublicKey delegateKey, long ttlSeconds) throws Exception {
        return buildAgentToken(issuerKeyPair, "test-key-1", iss, sub, delegateKey, ttlSeconds);
    }

    public static String buildAgentToken(KeyPair issuerKeyPair, String kid, String iss, String sub,
                                          PublicKey delegateKey, long ttlSeconds) throws Exception {
        return buildToken(issuerKeyPair, kid, "aa-agent+jwt", "aauth-agent.json",
                iss, sub, null, delegateKey, ttlSeconds);
    }

    /**
     * Build an aa-auth+jwt token signed by the issuer's Ed25519 key.
     */
    public static String buildAuthToken(KeyPair issuerKeyPair, String iss, String agentClaim,
                                         PublicKey delegateKey, long ttlSeconds) throws Exception {
        return buildToken(issuerKeyPair, "test-key-1", "aa-auth+jwt", "aauth-person.json",
                iss, null, agentClaim, delegateKey, ttlSeconds);
    }

    private static String buildToken(KeyPair issuerKeyPair, String kid, String typ, String dwk,
                                      String iss, String sub, String agentClaim,
                                      PublicKey delegateKey, long ttlSeconds) throws Exception {
        // Build the cnf.jwk from the delegate's public key
        byte[] rawDelegateKey = extractEd25519RawBytes(delegateKey);

        Instant now = Instant.now();

        var claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(iss)
                .claim("dwk", dwk)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)));

        if (sub != null) {
            claimsBuilder.subject(sub);
        }
        if (agentClaim != null) {
            claimsBuilder.claim("agent", agentClaim);
        }

        // cnf.jwk
        Map<String, Object> cnfJwk = Map.of(
                "kty", "OKP",
                "crv", "Ed25519",
                "x", Base64.getUrlEncoder().withoutPadding().encodeToString(rawDelegateKey)
        );
        claimsBuilder.claim("cnf", Map.of("jwk", cnfJwk));

        // Sign with issuer's key
        byte[] rawPrivateKey = issuerKeyPair.getPrivate().getEncoded();
        // Extract raw 32-byte seed from PKCS#8 encoded Ed25519 private key
        byte[] seed = new byte[32];
        System.arraycopy(rawPrivateKey, rawPrivateKey.length - 32, seed, 0, 32);
        byte[] rawPubKey = extractEd25519RawBytes(issuerKeyPair.getPublic());

        OctetKeyPair signingKey = new OctetKeyPair.Builder(
                Curve.Ed25519,
                com.nimbusds.jose.util.Base64URL.encode(rawPubKey))
                .d(com.nimbusds.jose.util.Base64URL.encode(seed))
                .keyID(kid)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .type(new JOSEObjectType(typ))
                .keyID(kid)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
        signedJWT.sign(new Ed25519Signer(signingKey));

        return signedJWT.serialize();
    }

    private static byte[] extractEd25519RawBytes(PublicKey key) {
        // Java's EdECPublicKey encodes as X.509: the raw 32 bytes are at the end
        byte[] encoded = key.getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return raw;
    }
}
