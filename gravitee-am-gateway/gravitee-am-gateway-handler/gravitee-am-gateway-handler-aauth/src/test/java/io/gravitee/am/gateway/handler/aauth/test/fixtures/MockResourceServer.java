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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * WireMock-backed mock resource server that serves metadata, JWKS, and can issue
 * {@code aa-resource+jwt} tokens for testing.
 */
public class MockResourceServer {

    private static final String KID = "rs-key-1";

    private final WireMockServer server;
    private OctetKeyPair signingKey;

    public MockResourceServer() {
        this.server = new WireMockServer(wireMockConfig().dynamicPort());
    }

    public MockResourceServer(int port) {
        this.server = new WireMockServer(wireMockConfig().port(port));
    }

    public MockResourceServer start() throws Exception {
        server.start();
        WireMock.configureFor("localhost", server.port());
        generateSigningKey();
        stubMetadataAndJwks();
        return this;
    }

    public void stop() {
        server.stop();
    }

    public String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /**
     * Issue a resource token (aa-resource+jwt) with the given claims.
     */
    public String issueResourceToken(String audience, String agent, String agentJkt, String scope)
            throws Exception {
        long now = Instant.now().getEpochSecond();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .type(new JOSEObjectType("aa-resource+jwt"))
                .keyID(KID)
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(baseUrl())
                .claim("dwk", "aauth-resource.json")
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .claim("agent", agent)
                .claim("agent_jkt", agentJkt)
                .claim("scope", scope)
                .issueTime(new Date(now * 1000))
                .expirationTime(new Date((now + 300) * 1000))
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new Ed25519Signer(signingKey));
        return jwt.serialize();
    }

    /**
     * Issue a resource token with custom iss/aud/typ/dwk for negative testing.
     */
    public String issueCustomResourceToken(String typ, String dwk, String iss, String aud,
                                            String agent, String agentJkt, long iat, long exp)
            throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .type(typ != null ? new JOSEObjectType(typ) : null)
                .keyID(KID)
                .build();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(iss)
                .audience(aud)
                .jwtID(UUID.randomUUID().toString())
                .claim("agent", agent)
                .claim("agent_jkt", agentJkt)
                .claim("scope", "read")
                .issueTime(new Date(iat * 1000))
                .expirationTime(new Date(exp * 1000));

        if (dwk != null) {
            claimsBuilder.claim("dwk", dwk);
        }

        SignedJWT jwt = new SignedJWT(header, claimsBuilder.build());
        jwt.sign(new Ed25519Signer(signingKey));
        return jwt.serialize();
    }

    private void generateSigningKey() throws Exception {
        // Generate Ed25519 keypair using Java native, then build Nimbus OKP for signing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        EdECPublicKey pubKey = (EdECPublicKey) kp.getPublic();
        EdECPoint point = pubKey.getPoint();

        // Convert to raw 32-byte Ed25519 encoding per RFC 8032
        byte[] yBytes = point.getY().toByteArray();
        byte[] raw = new byte[32];
        for (int i = 0; i < Math.min(yBytes.length, 32); i++) {
            raw[i] = yBytes[yBytes.length - 1 - i];
        }
        if (point.isXOdd()) {
            raw[31] |= (byte) 0x80;
        }

        // Get d (private key) bytes from PKCS#8 encoding
        byte[] pkcs8 = kp.getPrivate().getEncoded();
        byte[] d = new byte[32];
        System.arraycopy(pkcs8, pkcs8.length - 32, d, 0, 32);

        signingKey = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(raw))
                .d(Base64URL.encode(d))
                .keyID(KID)
                .build();
    }

    private void stubMetadataAndJwks() {
        String jwksUri = baseUrl() + "/jwks.json";

        // Resource metadata
        stubFor(get(urlEqualTo("/.well-known/aauth-resource.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"issuer":"%s","jwks_uri":"%s"}
                                """.formatted(baseUrl(), jwksUri))));

        // JWKS with the public key
        OctetKeyPair publicKey = signingKey.toPublicJWK();
        stubFor(get(urlEqualTo("/jwks.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/jwk-set+json")
                        .withBody("""
                                {"keys":[{"kty":"OKP","crv":"Ed25519","kid":"%s","x":"%s"}]}
                                """.formatted(KID, publicKey.getX().toString()))));
    }
}
