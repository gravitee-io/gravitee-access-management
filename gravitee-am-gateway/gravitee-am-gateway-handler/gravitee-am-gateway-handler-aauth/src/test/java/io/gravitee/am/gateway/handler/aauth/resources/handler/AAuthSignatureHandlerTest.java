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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.gateway.handler.aauth.signing.AAuthSignatureVerifier;
import io.gravitee.am.gateway.handler.aauth.signing.ReplayDetector;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.SignatureSchemeFactory;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestSignatureBuilder;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import java.security.KeyPair;
import java.util.Map;

/**
 * Tests for AAuthSignatureHandler — the Vert.x handler that verifies HTTP Message Signatures.
 */
public class AAuthSignatureHandlerTest extends RxWebTestBase {

    private static final String TEST_PATH = "/_test_protected";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        SignatureSchemeFactory schemeFactory = new SignatureSchemeFactory();
        ReplayDetector replayDetector = new ReplayDetector();
        AAuthSignatureVerifier verifier = new AAuthSignatureVerifier(schemeFactory, replayDetector);
        AAuthSignatureHandler handler = new AAuthSignatureHandler(verifier);

        router.route(TEST_PATH)
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create())
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(200).setStatusMessage("OK").end("success"));
    }

    @Test
    public void shouldReturn200_forValidSignedGetRequest() throws Exception {
        KeyPair keyPair = TestAgentKeyPairFactory.ed25519();
        Map<String, String> sigHeaders = TestSignatureBuilder.signGet(
                "GET", "localhost:" + server.actualPort(), TEST_PATH, keyPair);

        testRequest(
                HttpMethod.GET,
                TEST_PATH,
                req -> sigHeaders.forEach((name, value) -> req.putHeader(name, value)),
                null,
                200,
                "OK",
                null
        );
    }

    @Test
    public void shouldReturn401WithPseudonymRequirement_forUnsignedRequest() throws Exception {
        testRequest(
                HttpMethod.GET,
                TEST_PATH,
                null,
                resp -> {
                    String requirement = resp.getHeader("AAuth-Requirement");
                    assertNotNull("Should have AAuth-Requirement header", requirement);
                    assertTrue("Should contain requirement=pseudonym",
                            requirement.contains("requirement=pseudonym"));
                },
                401,
                "Unauthorized",
                null
        );
    }

    @Test
    public void shouldReturn401_forTamperedSignature() throws Exception {
        KeyPair keyPair = TestAgentKeyPairFactory.ed25519();
        Map<String, String> sigHeaders = TestSignatureBuilder.signGet(
                "GET", "localhost:" + server.actualPort(), TEST_PATH, keyPair);

        // Tamper with the signature
        String originalSig = sigHeaders.get("Signature");
        String tamperedSig = originalSig.substring(0, originalSig.length() - 2) + "X:";

        testRequest(
                HttpMethod.GET,
                TEST_PATH,
                req -> {
                    sigHeaders.forEach((name, value) -> req.putHeader(name, value));
                    req.putHeader("Signature", tamperedSig);
                },
                resp -> {
                    String error = resp.getHeader("Signature-Error");
                    assertNotNull("Should have Signature-Error header", error);
                    assertTrue("Should contain error=invalid_signature",
                            error.contains("invalid_signature"));
                },
                401,
                "Unauthorized",
                null
        );
    }

    @Test
    public void shouldReturn401_whenMissingSignatureInputHeader() throws Exception {
        testRequest(
                HttpMethod.GET,
                TEST_PATH,
                req -> {
                    req.putHeader("Signature", "sig=:dGVzdA==:");
                    req.putHeader("Signature-Key", "sig=hwk;kty=\"OKP\";crv=\"Ed25519\";x=\"test\"");
                },
                resp -> {
                    String error = resp.getHeader("Signature-Error");
                    assertNotNull("Should have Signature-Error header", error);
                },
                401,
                "Unauthorized",
                null
        );
    }

    @Test
    public void shouldReturn200_forValidSignedPostWithContentDigest() throws Exception {
        KeyPair keyPair = TestAgentKeyPairFactory.ed25519();
        byte[] body = "{\"resource_token\": \"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, String> sigHeaders = TestSignatureBuilder.signPost(
                "POST", "localhost:" + server.actualPort(), TEST_PATH,
                "application/json", body, keyPair);

        testRequest(
                HttpMethod.POST,
                TEST_PATH,
                req -> {
                    sigHeaders.forEach((name, value) -> req.putHeader(name, value));
                    req.end(io.vertx.core.buffer.Buffer.buffer(body));
                },
                null,
                200,
                "OK",
                null
        );
    }
}
