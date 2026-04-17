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

import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockResourceServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.interfaces.EdECPublicKey;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Tests for {@link ResourceTokenValidator} using a WireMock-backed resource server.
 */
public class ResourceTokenValidatorTest {

    private MockResourceServer resourceServer;
    private ResourceTokenValidator validator;
    private KeyPair agentKeyPair;
    private String agentThumbprint;

    @Before
    public void setUp() throws Exception {
        resourceServer = new MockResourceServer().start();
        validator = new ResourceTokenValidator(new AgentMetadataFetcher());
        agentKeyPair = TestAgentKeyPairFactory.ed25519();
        agentThumbprint = computeJwkThumbprint(agentKeyPair);
    }

    @After
    public void tearDown() {
        resourceServer.stop();
    }

    @Test
    public void shouldValidate_validResourceToken() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        String resourceToken = resourceServer.issueResourceToken(
                psUrl, "aauth:bot@agent.example", agentThumbprint, "read write");

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        ResourceTokenClaims claims = validator.validate(resourceToken, verification, psUrl);

        assertEquals(resourceServer.baseUrl(), claims.iss());
        assertEquals(psUrl, claims.aud());
        assertEquals("aauth:bot@agent.example", claims.agent());
        assertEquals(agentThumbprint, claims.agentJkt());
        assertEquals("read write", claims.scope());
        assertNotNull(claims.jti());
        assertTrue("iat should be recent", claims.iat() > 0);
        assertTrue("exp should be in the future", claims.exp() > claims.iat());
    }

    @Test
    public void shouldReject_invalidTyp() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        long now = java.time.Instant.now().getEpochSecond();
        String token = resourceServer.issueCustomResourceToken(
                "at+jwt", "aauth-resource.json", resourceServer.baseUrl(), psUrl,
                "aauth:bot@agent.example", agentThumbprint, now, now + 300);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(token, verification, psUrl);
            fail("Should reject invalid typ");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("typ"));
        }
    }

    @Test
    public void shouldReject_invalidDwk() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        long now = java.time.Instant.now().getEpochSecond();
        String token = resourceServer.issueCustomResourceToken(
                "aa-resource+jwt", "aauth-agent.json", resourceServer.baseUrl(), psUrl,
                "aauth:bot@agent.example", agentThumbprint, now, now + 300);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(token, verification, psUrl);
            fail("Should reject invalid dwk");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("dwk"));
        }
    }

    @Test
    public void shouldReject_expiredToken() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        long now = java.time.Instant.now().getEpochSecond();
        String token = resourceServer.issueCustomResourceToken(
                "aa-resource+jwt", "aauth-resource.json", resourceServer.baseUrl(), psUrl,
                "aauth:bot@agent.example", agentThumbprint, now - 600, now - 300);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(token, verification, psUrl);
            fail("Should reject expired token");
        } catch (ResourceTokenException e) {
            assertEquals("expired_resource_token", e.getErrorCode());
        }
    }

    @Test
    public void shouldReject_wrongAudience() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        String resourceToken = resourceServer.issueResourceToken(
                "https://other-ps.example.com/aauth", "aauth:bot@agent.example",
                agentThumbprint, "read");

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(resourceToken, verification, psUrl);
            fail("Should reject wrong audience");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("aud"));
        }
    }

    @Test
    public void shouldReject_agentMismatch() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        String resourceToken = resourceServer.issueResourceToken(
                psUrl, "https://other-agent.example", agentThumbprint, "read");

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(resourceToken, verification, psUrl);
            fail("Should reject agent mismatch");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("agent"));
        }
    }

    @Test
    public void shouldReject_agentJktMismatch() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        String resourceToken = resourceServer.issueResourceToken(
                psUrl, "aauth:bot@agent.example", "wrong-thumbprint", "read");

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(resourceToken, verification, psUrl);
            fail("Should reject agent_jkt mismatch");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("agent_jkt"));
        }
    }

    @Test
    public void shouldReject_futureIat() throws Exception {
        String psUrl = "https://ps.example.com/aauth";
        long now = java.time.Instant.now().getEpochSecond();
        String token = resourceServer.issueCustomResourceToken(
                "aa-resource+jwt", "aauth-resource.json", resourceServer.baseUrl(), psUrl,
                "aauth:bot@agent.example", agentThumbprint, now + 600, now + 900);

        VerificationResult verification = new VerificationResult(
                "hwk", "sig", agentKeyPair.getPublic(), agentThumbprint, "https://agent.example", "aauth:bot@agent.example");

        try {
            validator.validate(token, verification, psUrl);
            fail("Should reject future iat");
        } catch (ResourceTokenException e) {
            assertEquals("invalid_resource_token", e.getErrorCode());
            assertTrue(e.getMessage().contains("iat"));
        }
    }

    /**
     * Compute a JWK Thumbprint (RFC 7638) for an Ed25519 public key.
     */
    private String computeJwkThumbprint(KeyPair keyPair) throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();
        // JWK Thumbprint per RFC 7638: SHA-256 of canonical JSON {"crv":"Ed25519","kty":"OKP","x":"..."}
        String canonical = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + x + "\"}";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
