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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive manual test for the AAUTH deferred authorization flow.
 * <p>
 * 1. Sends POST /aauth/token → expects 202 with interaction URL + code
 * 2. Prints the URL for the user to open in their browser
 * 3. Polls GET /aauth/pending/{id} following the spec (Retry-After, timeout)
 * 4. When the user completes consent, prints the auth token
 * <p>
 * Usage:
 * <pre>
 *   mvn exec:java -pl gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-aauth \
 *     -Dexec.mainClass="io.gravitee.am.gateway.handler.aauth.manual.AAuthTokenEndpointManualTest" \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="http://localhost:8092 testdomain"
 * </pre>
 */
public class AAuthTokenEndpointManualTest {

    private static final int RESOURCE_SERVER_PORT = 9999;
    private static final int MAX_POLL_DURATION_SECONDS = 300; // 5 minutes max
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String amUrl = args.length > 0 ? args[0] : "http://localhost:8092";
        String domain = args.length > 1 ? args[1] : "testdomain";

        System.out.println("=== AAUTH Deferred Authorization Manual Test ===");
        System.out.println("AM URL:  " + amUrl);
        System.out.println("Domain:  " + domain);
        System.out.println();

        MockResourceServer resourceServer = new MockResourceServer(RESOURCE_SERVER_PORT);
        try {
            // Step 1: Start mock resource server
            resourceServer.start();
            System.out.println("[1] Mock resource server running at " + resourceServer.baseUrl());

            // Step 2: Generate agent keypair
            KeyPair agentKeyPair = TestAgentKeyPairFactory.ed25519();
            String agentX = TestAgentKeyPairFactory.ed25519PublicKeyX();
            String agentThumbprint = computeJwkThumbprint(agentX);
            System.out.println("[2] Agent keypair ready (thumbprint: " + agentThumbprint.substring(0, 12) + "...)");

            // Step 3: Fetch PS metadata
            String metadataUrl = amUrl + "/" + domain + "/aauth/.well-known/aauth-person.json";
            var metadataResponse = HTTP_CLIENT.send(
                    HttpRequest.newBuilder().uri(URI.create(metadataUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (metadataResponse.statusCode() != 200) {
                System.err.println("ERROR: PS metadata returned " + metadataResponse.statusCode());
                System.err.println("       Is AM running? Is AAUTH enabled on domain '" + domain + "'?");
                return;
            }

            @SuppressWarnings("unchecked")
            var metadata = OBJECT_MAPPER.readValue(metadataResponse.body(), Map.class);
            String psIssuerUrl = (String) metadata.get("issuer");
            String tokenEndpointUrl = (String) metadata.get("token_endpoint");
            System.out.println("[3] PS issuer: " + psIssuerUrl);

            // Step 4: Issue resource token
            String agentIdentity = resourceServer.baseUrl();
            String resourceToken = resourceServer.issueResourceToken(
                    psIssuerUrl, agentIdentity, agentThumbprint, "read write");
            System.out.println("[4] Resource token issued (scope: read write)");

            // Step 5: POST /aauth/token (signed)
            String jsonBody = OBJECT_MAPPER.writeValueAsString(Map.of("resource_token", resourceToken));
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);

            URI tokenUri = URI.create(tokenEndpointUrl);
            String authority = tokenUri.getHost() + (tokenUri.getPort() > 0 ? ":" + tokenUri.getPort() : "");
            String path = tokenUri.getPath();

            Map<String, String> sigHeaders = TestSignatureBuilder.signPost(
                    "POST", authority, path, "application/json", bodyBytes, agentKeyPair);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpointUrl))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            sigHeaders.forEach(requestBuilder::header);

            System.out.println("[5] Sending POST " + tokenEndpointUrl);
            var response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            System.out.println("    Status: " + response.statusCode());
            printJson("    Body", response.body());

            if (response.statusCode() != 202) {
                System.err.println("ERROR: Expected 202 Accepted, got " + response.statusCode());
                return;
            }

            // Extract pending URL and interaction info
            String pendingUrl = response.headers().firstValue("Location").orElse(null);
            String requirement = response.headers().firstValue("AAuth-Requirement").orElse("");

            if (pendingUrl == null) {
                System.err.println("ERROR: No Location header");
                return;
            }

            // Parse interaction URL and code from AAuth-Requirement header
            String interactUrl = parseParam(requirement, "url");
            String interactionCode = parseParam(requirement, "code");

            System.out.println();
            System.out.println("============================================================");
            System.out.println("  Interaction URL (Phase 8b — not yet implemented):");
            if (interactUrl != null && interactionCode != null) {
                System.out.println("  " + interactUrl + "?code=" + interactionCode);
            }
            System.out.println();
            System.out.println("  Pending URL:      " + pendingUrl);
            System.out.println("  Interaction code: " + interactionCode);
            System.out.println("============================================================");
            System.out.println();

            // Step 6: Poll the pending URL per spec Section 12.4
            // The interaction endpoint is not yet implemented (Phase 8b), so polling
            // will keep returning 202/pending until the request expires.
            System.out.println("[6] Polling " + pendingUrl + " (max " + MAX_POLL_DURATION_SECONDS + "s)...");
            System.out.println("    (The request will stay 'pending' until Phase 8b adds the interaction endpoint)");

            long startTime = System.currentTimeMillis();
            int pollInterval = DEFAULT_POLL_INTERVAL_SECONDS;
            int pollCount = 0;

            while (true) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > MAX_POLL_DURATION_SECONDS) {
                    System.out.println("    Timeout after " + elapsed + "s. Giving up.");
                    break;
                }

                // Wait before polling (respect Retry-After)
                Thread.sleep(pollInterval * 1000L);
                pollCount++;

                // Sign the GET request
                URI pollUri = URI.create(pendingUrl);
                String pollAuthority = pollUri.getHost() + (pollUri.getPort() > 0 ? ":" + pollUri.getPort() : "");
                Map<String, String> pollSigHeaders = TestSignatureBuilder.signGet(
                        "GET", pollAuthority, pollUri.getPath(), agentKeyPair);

                var pollRequestBuilder = HttpRequest.newBuilder().uri(pollUri).GET();
                pollSigHeaders.forEach(pollRequestBuilder::header);

                var pollResponse = HTTP_CLIENT.send(pollRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int pollStatus = pollResponse.statusCode();

                // Parse Retry-After if present
                pollResponse.headers().firstValue("Retry-After").ifPresent(ra -> {
                    // Could be used to adjust poll interval
                });

                if (pollStatus == 200) {
                    // Auth token received
                    System.out.println("    Poll #" + pollCount + " -> 200 OK");
                    System.out.println();
                    System.out.println("=== AUTH TOKEN RECEIVED ===");
                    printJson("Response", pollResponse.body());

                    @SuppressWarnings("unchecked")
                    var responseMap = OBJECT_MAPPER.readValue(pollResponse.body(), Map.class);
                    String authToken = (String) responseMap.get("auth_token");
                    if (authToken != null) {
                        System.out.println();
                        decodeAndPrintJwt(authToken);
                    }
                    break;

                } else if (pollStatus == 202) {
                    @SuppressWarnings("unchecked")
                    var pollBody = OBJECT_MAPPER.readValue(pollResponse.body(), Map.class);
                    String status = (String) pollBody.get("status");
                    System.out.println("    Poll #" + pollCount + " -> 202 (" + status + ") [" + elapsed + "s elapsed]");

                } else if (pollStatus == 429) {
                    // Slow down — increase interval per spec Section 12.3
                    pollInterval = Math.min(pollInterval + 5, 30);
                    System.out.println("    Poll #" + pollCount + " -> 429 slow_down (interval now " + pollInterval + "s)");

                } else {
                    // Terminal response (403, 408, 410, etc.)
                    System.out.println("    Poll #" + pollCount + " -> " + pollStatus + " (terminal)");
                    printJson("Response", pollResponse.body());
                    break;
                }
            }

        } finally {
            resourceServer.stop();
            System.out.println("\nMock resource server stopped.");
        }
    }

    private static String parseParam(String header, String paramName) {
        // Parse paramName="value" from structured header
        Pattern pattern = Pattern.compile(paramName + "=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String computeJwkThumbprint(String x) throws Exception {
        String canonical = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + x + "\"}";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static void printJson(String label, String json) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(json, Object.class);
            System.out.println("    " + label + ": " + OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(parsed).replace("\n", "\n    "));
        } catch (Exception e) {
            System.out.println("    " + label + ": " + json);
        }
    }

    private static void decodeAndPrintJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String claimsJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                System.out.println("=== AUTH TOKEN CLAIMS ===");
                printJson("Header", headerJson);
                printJson("Claims", claimsJson);
            }
        } catch (Exception e) {
            System.out.println("(Failed to decode JWT: " + e.getMessage() + ")");
        }
    }
}
