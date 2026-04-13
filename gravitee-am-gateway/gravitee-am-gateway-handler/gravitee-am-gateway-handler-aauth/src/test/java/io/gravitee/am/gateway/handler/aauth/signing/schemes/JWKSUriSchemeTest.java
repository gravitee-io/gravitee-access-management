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

import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockAgentMetadataServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class JWKSUriSchemeTest {

    private MockAgentMetadataServer mockServer;
    private JWKSUriScheme scheme;

    @Before
    public void setUp() {
        mockServer = new MockAgentMetadataServer().start();
        mockServer.stubMetadata(mockServer.baseUrl(), mockServer.baseUrl() + "/jwks.json", "Test Agent");
        mockServer.stubJwksWithEd25519("key-1", TestAgentKeyPairFactory.ed25519PublicKeyX());

        scheme = new JWKSUriScheme(new AgentMetadataFetcher());
    }

    @After
    public void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    public void shouldResolveKey_whenKnownKid() throws Exception {
        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "jwks_uri",
                Map.of("id", mockServer.baseUrl(), "kid", "key-1"));

        ResolvedKey resolved = scheme.resolve(keyInfo);

        assertNotNull(resolved.publicKey());
        assertEquals("Ed25519", resolved.algorithm());
        assertNotNull(resolved.jwkThumbprint());
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldThrowUnknownKey_whenKidNotFound() throws Exception {
        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "jwks_uri",
                Map.of("id", mockServer.baseUrl(), "kid", "nonexistent-key"));

        scheme.resolve(keyInfo);
    }

    @Test
    public void shouldRefetchJwksOnce_beforeReturningUnknownKey() throws Exception {
        try {
            SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "jwks_uri",
                    Map.of("id", mockServer.baseUrl(), "kid", "nonexistent-key"));
            scheme.resolve(keyInfo);
            fail("Should have thrown");
        } catch (SignatureVerificationException e) {
            assertEquals("unknown_key", e.getErrorCode());
            assertEquals("JWKS should have been fetched twice (initial + retry)", 2, mockServer.jwksRequestCount());
        }
    }

    @Test
    public void shouldResolveP256Key_whenKnownKid() throws Exception {
        // Add a P-256 key to the mock server
        var kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var ecPub = (java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic();
        byte[] xBytes = toFixedLength(ecPub.getW().getAffineX().toByteArray(), 32);
        byte[] yBytes = toFixedLength(ecPub.getW().getAffineY().toByteArray(), 32);
        String x = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes);
        String y = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes);

        // Re-stub JWKS with a P-256 key
        com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/jwks.json"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", "application/jwk-set+json")
                        .withBody("""
                                {"keys":[{"kty":"EC","crv":"P-256","kid":"ec-key","x":"%s","y":"%s"}]}
                                """.formatted(x, y))));

        // Use a fresh fetcher to avoid cache
        var freshScheme = new JWKSUriScheme(new AgentMetadataFetcher());
        SignatureKeyInfo keyInfo = new SignatureKeyInfo("sig", "jwks_uri",
                Map.of("id", mockServer.baseUrl(), "kid", "ec-key"));

        ResolvedKey resolved = freshScheme.resolve(keyInfo);

        assertNotNull(resolved.publicKey());
        assertEquals("SHA256withECDSA", resolved.algorithm());
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldThrowInvalidKey_whenMissingId() throws Exception {
        scheme.resolve(new SignatureKeyInfo("sig", "jwks_uri", Map.of("kid", "key-1")));
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldThrowInvalidKey_whenMissingKid() throws Exception {
        scheme.resolve(new SignatureKeyInfo("sig", "jwks_uri", Map.of("id", mockServer.baseUrl())));
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldThrowInvalidKey_whenAgentUnreachable() throws Exception {
        scheme.resolve(new SignatureKeyInfo("sig", "jwks_uri",
                Map.of("id", "http://localhost:1", "kid", "key-1")));
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldThrowInvalidKey_whenMetadataHasNoJwksUri() throws Exception {
        // Stub metadata without jwks_uri
        com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/.well-known/aauth-agent.json"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"issuer":"%s"}
                                """.formatted(mockServer.baseUrl()))));

        var freshScheme = new JWKSUriScheme(new AgentMetadataFetcher());
        freshScheme.resolve(new SignatureKeyInfo("sig", "jwks_uri",
                Map.of("id", mockServer.baseUrl(), "kid", "key-1")));
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
