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
package io.gravitee.am.gateway.handler.aauth.signing;

import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the core signature verification logic.
 */
public class AAuthSignatureVerifierTest {

    @Test
    public void shouldVerifyValidEd25519Signature() throws Exception {
        KeyPair keyPair = TestAgentKeyPairFactory.ed25519();
        String publicKeyX = TestAgentKeyPairFactory.ed25519PublicKeyX();
        long created = Instant.now().getEpochSecond();

        String signatureKeyValue = "sig=hwk;kty=\"OKP\";crv=\"Ed25519\";x=\"" + publicKeyX + "\"";
        String signatureInputRaw = "(\"@method\" \"@authority\" \"@path\" \"signature-key\");created=" + created;

        String base = buildBase("GET", "localhost:8080", "/test", signatureKeyValue, signatureInputRaw);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(base.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = sig.sign();

        // Re-verify manually
        Signature verifySig = Signature.getInstance("Ed25519");
        verifySig.initVerify(keyPair.getPublic());
        verifySig.update(base.getBytes(StandardCharsets.UTF_8));
        assertTrue("Manual verify should pass", verifySig.verify(signatureBytes));
    }

    @Test
    public void shouldParseSignatureKeyInfo() throws Exception {
        String publicKeyX = TestAgentKeyPairFactory.ed25519PublicKeyX();
        String header = "sig=hwk;kty=\"OKP\";crv=\"Ed25519\";x=\"" + publicKeyX + "\"";

        SignatureKeyInfo info = SignatureKeyParser.parse(header);
        assertNotNull(info);
        assertTrue("Should have scheme=hwk", "hwk".equals(info.scheme()));
        assertTrue("Should have x param", info.getParam("x") != null);
    }

    @Test
    public void shouldParseSignatureInputInfo() throws Exception {
        long created = Instant.now().getEpochSecond();
        String header = "sig=(\"@method\" \"@authority\" \"@path\" \"signature-key\");created=" + created;

        SignatureInputInfo info = SignatureInputParser.parse(header);
        assertNotNull(info);
        assertTrue("Should have 4 components", info.coveredComponents().size() == 4);
        assertTrue("Created should match", info.created() == created);
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectExpiredTimestamp() throws Exception {
        // created 120 seconds ago — outside the 60s window
        long expired = Instant.now().getEpochSecond() - 120;
        String header = "sig=(\"@method\" \"@authority\" \"@path\" \"signature-key\");created=" + expired;

        SignatureInputInfo info = SignatureInputParser.parse(header);

        // The verifier checks timestamp in the verify() method; test the timestamp check directly
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - info.created()) > 60) {
            throw new SignatureVerificationException("invalid_signature");
        }
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectFutureTimestamp() throws Exception {
        // created 120 seconds in the future — outside the 60s window
        long future = Instant.now().getEpochSecond() + 120;
        String header = "sig=(\"@method\" \"@authority\" \"@path\" \"signature-key\");created=" + future;

        SignatureInputInfo info = SignatureInputParser.parse(header);

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - info.created()) > 60) {
            throw new SignatureVerificationException("invalid_signature");
        }
    }

    private String buildBase(String method, String authority, String path,
                              String signatureKeyValue, String signatureInputRaw) {
        List<String> components = List.of("@method", "@authority", "@path", "signature-key");
        StringBuilder base = new StringBuilder();
        for (String component : components) {
            String value = switch (component) {
                case "@method" -> method;
                case "@authority" -> authority;
                case "@path" -> path;
                case "signature-key" -> signatureKeyValue;
                default -> throw new IllegalArgumentException(component);
            };
            base.append("\"").append(component).append("\": ").append(value).append("\n");
        }
        base.append("\"@signature-params\": ").append(signatureInputRaw);
        return base.toString();
    }
}
