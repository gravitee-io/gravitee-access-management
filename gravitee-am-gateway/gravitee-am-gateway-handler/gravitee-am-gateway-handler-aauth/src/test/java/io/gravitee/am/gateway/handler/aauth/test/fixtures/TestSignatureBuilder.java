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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds complete RFC 9421 HTTP Message Signatures for tests.
 * Uses Java native crypto (no Nimbus for signing).
 */
public final class TestSignatureBuilder {

    private TestSignatureBuilder() {
    }

    /**
     * Build signature headers for a GET request using Ed25519 (HWK scheme).
     */
    public static Map<String, String> signGet(String method, String authority, String path,
                                               KeyPair keyPair) throws Exception {
        return sign(method, authority, path, null, null, keyPair, null, null);
    }

    /**
     * Build signature headers for a POST request with body using Ed25519 (HWK scheme).
     */
    public static Map<String, String> signPost(String method, String authority, String path,
                                                String contentType, byte[] body,
                                                KeyPair keyPair) throws Exception {
        return sign(method, authority, path, contentType, body, keyPair, null, null);
    }

    /**
     * Build signature headers for a GET request using the JWKS URI scheme.
     */
    public static Map<String, String> signGetJwksUri(String method, String authority, String path,
                                                      KeyPair keyPair, String agentMetadataUrl,
                                                      String kid) throws Exception {
        return sign(method, authority, path, null, null, keyPair, agentMetadataUrl, kid);
    }

    /**
     * Build signature headers for a POST request using the JWKS URI scheme.
     */
    public static Map<String, String> signPostJwksUri(String method, String authority, String path,
                                                       String contentType, byte[] body,
                                                       KeyPair keyPair, String agentMetadataUrl,
                                                       String kid) throws Exception {
        return sign(method, authority, path, contentType, body, keyPair, agentMetadataUrl, kid);
    }

    private static Map<String, String> sign(String method, String authority, String path,
                                             String contentType, byte[] body,
                                             KeyPair keyPair, String jwksUriMetadataUrl,
                                             String kid) throws Exception {
        String label = "sig";
        long created = Instant.now().getEpochSecond();

        // Build Signature-Key header — HWK or JWKS URI scheme
        String signatureKey;
        if (jwksUriMetadataUrl != null) {
            signatureKey = label + "=jwks_uri;id=\"" + jwksUriMetadataUrl + "\";kid=\"" + kid + "\"";
        } else {
            String publicKeyX = TestAgentKeyPairFactory.ed25519PublicKeyX();
            signatureKey = label + "=hwk;kty=\"OKP\";crv=\"Ed25519\";x=\"" + publicKeyX + "\"";
        }

        // Determine covered components
        List<String> components;
        String contentDigestHeader = null;

        if (body != null && contentType != null) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            contentDigestHeader = "sha-256=:" + Base64.getEncoder().encodeToString(digest) + ":";
            components = List.of("@method", "@authority", "@path", "content-type", "content-digest", "signature-key");
        } else {
            components = List.of("@method", "@authority", "@path", "signature-key");
        }

        // Build Signature-Input header
        String componentsList = components.stream()
                .map(c -> "\"" + c + "\"")
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        String signatureInputValue = "(" + componentsList + ");created=" + created;
        String signatureInput = label + "=" + signatureInputValue;

        // Build signature base
        StringBuilder base = new StringBuilder();
        for (String component : components) {
            String value = switch (component) {
                case "@method" -> method;
                case "@authority" -> authority;
                case "@path" -> path;
                case "content-type" -> contentType;
                case "content-digest" -> contentDigestHeader;
                case "signature-key" -> signatureKey;
                default -> throw new IllegalArgumentException("Unknown component: " + component);
            };
            base.append("\"").append(component).append("\": ").append(value).append("\n");
        }
        base.append("\"@signature-params\": ").append(signatureInputValue);

        // Sign with Java native Ed25519
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(base.toString().getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = sig.sign();

        String signature = label + "=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Signature-Key", signatureKey);
        headers.put("Signature-Input", signatureInput);
        headers.put("Signature", signature);
        if (contentDigestHeader != null) {
            headers.put("Content-Digest", contentDigestHeader);
        }
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        return headers;
    }
}
