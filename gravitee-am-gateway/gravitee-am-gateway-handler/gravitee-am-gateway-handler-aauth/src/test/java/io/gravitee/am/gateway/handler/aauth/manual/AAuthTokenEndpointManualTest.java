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
package io.gravitee.am.gateway.handler.aauth.manual;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockResourceServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestSignatureBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * Standalone manual test for the AAUTH token endpoint (POST /aauth/token).
 * <p>
 * Starts a mock resource server (WireMock on port 9999), generates an Ed25519 agent keypair,
 * issues a resource token, signs the HTTP request, and sends it to a running AM instance.
 * <p>
 * Usage:
 * <pre>
 *   # From IntelliJ: run main() directly
 *   # From CLI:
 *   mvn exec:java -pl gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-aauth \
 *     -Dexec.mainClass="io.gravitee.am.gateway.handler.aauth.manual.AAuthTokenEndpointManualTest" \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="http://localhost:8092 testdomain"
 * </pre>
 *
 * Prerequisites:
 * <ul>
 *   <li>AM gateway running locally</li>
 *   <li>A security domain created (e.g. "testdomain")</li>
 *   <li>Port 9999 available for the mock resource server</li>
 * </ul>
 */
public class AAuthTokenEndpointManualTest {

    private static final int RESOURCE_SERVER_PORT = 9999;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        // Parse arguments
        String amUrl = args.length > 0 ? args[0] : "http://localhost:8092";
        String domain = args.length > 1 ? args[1] : "testdomain";

        System.out.println("=== AAUTH Token Endpoint Manual Test ===");
        System.out.println("AM URL:  " + amUrl);
        System.out.println("Domain:  " + domain);
        System.out.println();

        // Step 1: Start mock resource server
        System.out.println("[Step 1] Starting mock resource server on port " + RESOURCE_SERVER_PORT + "...");
        MockResourceServer resourceServer = new MockResourceServer(RESOURCE_SERVER_PORT);
        try {
            resourceServer.start();
            System.out.println("         Mock resource server running at " + resourceServer.baseUrl());
            System.out.println("         Metadata: " + resourceServer.baseUrl() + "/.well-known/aauth-resource.json");
            System.out.println("         JWKS:     " + resourceServer.baseUrl() + "/jwks.json");
            System.out.println();

            // Step 2: Generate agent keypair and compute JWK thumbprint
            System.out.println("[Step 2] Generating Ed25519 agent keypair...");
            KeyPair agentKeyPair = TestAgentKeyPairFactory.ed25519();
            String agentX = TestAgentKeyPairFactory.ed25519PublicKeyX();
            String agentThumbprint = computeJwkThumbprint(agentX);
            System.out.println("         Public key x: " + agentX);
            System.out.println("         JWK Thumbprint: " + agentThumbprint);
            System.out.println();

            // Step 3: Fetch PS metadata to discover issuer URL
            System.out.println("[Step 3] Fetching PS metadata from AM...");
            String metadataUrl = amUrl + "/" + domain + "/aauth/.well-known/aauth-person.json";
            System.out.println("         GET " + metadataUrl);
            var metadataResponse = HTTP_CLIENT.send(
                    HttpRequest.newBuilder().uri(URI.create(metadataUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (metadataResponse.statusCode() != 200) {
                System.err.println("ERROR: Failed to fetch PS metadata: HTTP " + metadataResponse.statusCode());
                System.err.println("       Body: " + metadataResponse.body());
                System.err.println("       Is AM running? Is the domain '" + domain + "' created?");
                return;
            }

            @SuppressWarnings("unchecked")
            var metadata = OBJECT_MAPPER.readValue(metadataResponse.body(), Map.class);
            String psIssuerUrl = (String) metadata.get("issuer");
            String tokenEndpointUrl = (String) metadata.get("token_endpoint");
            System.out.println("         PS Issuer URL:     " + psIssuerUrl);
            System.out.println("         Token Endpoint:    " + tokenEndpointUrl);
            System.out.println();

            // Step 4: Issue resource token
            System.out.println("[Step 4] Issuing aa-resource+jwt...");
            String agentIdentity = resourceServer.baseUrl(); // agent pretends to be at the resource server URL for simplicity
            String resourceToken = resourceServer.issueResourceToken(
                    psIssuerUrl, agentIdentity, agentThumbprint, "read write");
            System.out.println("         Resource token (first 80 chars): " + resourceToken.substring(0, Math.min(80, resourceToken.length())) + "...");
            System.out.println("         Agent identity: " + agentIdentity);
            System.out.println("         Audience (PS):  " + psIssuerUrl);
            System.out.println("         Scope:          read write");
            System.out.println();

            // Step 5: Build request body
            String jsonBody = OBJECT_MAPPER.writeValueAsString(Map.of("resource_token", resourceToken));
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);

            // Step 6: Sign HTTP request
            System.out.println("[Step 5] Signing HTTP POST request...");
            URI tokenUri = URI.create(tokenEndpointUrl);
            String authority = tokenUri.getHost() + (tokenUri.getPort() > 0 ? ":" + tokenUri.getPort() : "");
            String path = tokenUri.getPath();

            Map<String, String> sigHeaders = TestSignatureBuilder.signPost(
                    "POST", authority, path,
                    "application/json", bodyBytes, agentKeyPair);
            System.out.println("         Authority: " + authority);
            System.out.println("         Path:      " + path);
            System.out.println("         Headers:");
            sigHeaders.forEach((name, value) ->
                    System.out.println("           " + name + ": " + truncate(value, 100)));
            System.out.println();

            // Step 7: Send POST to AM
            System.out.println("[Step 6] Sending POST to " + tokenEndpointUrl + "...");
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpointUrl))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));

            sigHeaders.forEach(requestBuilder::header);

            var response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            System.out.println();
            System.out.println("=== RESPONSE ===");
            System.out.println("Status:        " + response.statusCode());
            System.out.println("Cache-Control: " + response.headers().firstValue("Cache-Control").orElse("(not set)"));
            System.out.println("Content-Type:  " + response.headers().firstValue("Content-Type").orElse("(not set)"));
            System.out.println("Body:");
            // Pretty-print JSON
            try {
                Object json = OBJECT_MAPPER.readValue(response.body(), Object.class);
                System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            } catch (Exception e) {
                System.out.println(response.body());
            }

            // Step 8: Decode auth token if present
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                var responseMap = OBJECT_MAPPER.readValue(response.body(), Map.class);
                String authToken = (String) responseMap.get("auth_token");
                if (authToken != null) {
                    System.out.println();
                    System.out.println("=== AUTH TOKEN CLAIMS ===");
                    decodeAndPrintJwt(authToken);
                }
            }

            // Check for error headers
            response.headers().firstValue("Signature-Error").ifPresent(v ->
                    System.out.println("\nSignature-Error: " + v));
            response.headers().firstValue("AAuth-Requirement").ifPresent(v ->
                    System.out.println("\nAAuth-Requirement: " + v));

        } finally {
            resourceServer.stop();
            System.out.println("\nMock resource server stopped.");
        }
    }

    private static String computeJwkThumbprint(String x) throws Exception {
        // RFC 7638: SHA-256 of canonical JSON {"crv":"Ed25519","kty":"OKP","x":"<x>"}
        String canonical = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + x + "\"}";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static void decodeAndPrintJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String claimsJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

                System.out.println("Header:");
                Object header = OBJECT_MAPPER.readValue(headerJson, Object.class);
                System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(header));

                System.out.println("Claims:");
                Object claims = OBJECT_MAPPER.readValue(claimsJson, Object.class);
                System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(claims));
            }
        } catch (Exception e) {
            System.out.println("(Failed to decode JWT: " + e.getMessage() + ")");
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
