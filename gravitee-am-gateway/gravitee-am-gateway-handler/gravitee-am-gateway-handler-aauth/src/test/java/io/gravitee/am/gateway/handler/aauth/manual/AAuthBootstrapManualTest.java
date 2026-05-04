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
import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockAgentMetadataServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockResourceServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentTokenBuilder;
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
 * Interactive manual test for the AAUTH Bootstrap + Authorization flow.
 * <p>
 * Exercises the full ceremony:
 * 1. Bootstrap: POST /aauth/bootstrap (hwk) → 202 → user consent → poll → bootstrap_token
 * 2. Simulate Agent Server: build aa-agent+jwt from the bootstrap_token
 * 3. Announce: POST /aauth/bootstrap (jwt, empty body) → 204
 * 4. Authorization: POST /aauth/token (jwt) → 202 → user consent → poll → auth_token
 * <p>
 * Uses mock servers for the Agent Server (metadata + JWKS) and Resource Server.
 * <p>
 * Usage:
 * <pre>
 *   mvn exec:java -pl gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-aauth \
 *     -Dexec.mainClass="io.gravitee.am.gateway.handler.aauth.manual.AAuthBootstrapManualTest" \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="http://localhost:8092 testdomain"
 * </pre>
 */
public class AAuthBootstrapManualTest {

    private static final int AGENT_SERVER_PORT = 9998;
    private static final int RESOURCE_SERVER_PORT = 9999;
    private static final String AGENT_KID = "agent-key-1";
    private static final int MAX_POLL_SECONDS = 300;
    private static final int POLL_INTERVAL = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String amUrl = args.length > 0 ? args[0] : "http://localhost:8092";
        String domain = args.length > 1 ? args[1] : "testdomain";

        System.out.println("=== AAUTH Bootstrap + Authorization Manual Test ===");
        System.out.println("AM URL:  " + amUrl);
        System.out.println("Domain:  " + domain);
        System.out.println();

        // Agent Server's signing keypair (signs agent tokens)
        KeyPair issuerKeyPair = TestAgentKeyPairFactory.ed25519();
        String issuerX = TestAgentKeyPairFactory.ed25519PublicKeyX();

        // Agent CLI's ephemeral keypair (signs HTTP requests)
        KeyPair ephemeralKeyPair = TestAgentKeyPairFactory.ed25519();
        String ephemeralX = extractEd25519X(ephemeralKeyPair);
        String ephemeralThumbprint = computeJwkThumbprint(ephemeralX);

        MockAgentMetadataServer agentServer = new MockAgentMetadataServer(AGENT_SERVER_PORT);
        MockResourceServer resourceServer = new MockResourceServer(RESOURCE_SERVER_PORT);

        try {
            agentServer.start();
            String agentBaseUrl = agentServer.baseUrl();
            agentServer.stubMetadata(agentBaseUrl, agentBaseUrl + "/jwks.json", "Test AAUTH Agent");
            agentServer.stubJwksWithEd25519(AGENT_KID, issuerX);
            System.out.println("[setup] Agent server at " + agentBaseUrl);

            resourceServer.start();
            System.out.println("[setup] Resource server at " + resourceServer.baseUrl());

            // ============================================================
            // PHASE 1: BOOTSTRAP
            // ============================================================
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  PHASE 1: BOOTSTRAP CEREMONY                 ║");
            System.out.println("╚══════════════════════════════════════════════╝");

            // Fetch PS metadata
            String metadataUrl = amUrl + "/" + domain + "/aauth/.well-known/aauth-person.json";
            var metaResp = httpGet(metadataUrl);
            if (metaResp.statusCode() != 200) {
                System.err.println("ERROR: PS metadata returned " + metaResp.statusCode());
                return;
            }
            @SuppressWarnings("unchecked")
            var psMeta = MAPPER.readValue(metaResp.body(), Map.class);
            String psIssuerUrl = (String) psMeta.get("issuer");
            String tokenEndpointUrl = (String) psMeta.get("token_endpoint");
            String bootstrapEndpointUrl = (String) psMeta.get("bootstrap_endpoint");
            System.out.println("[1] PS issuer: " + psIssuerUrl);
            System.out.println("    bootstrap_endpoint: " + bootstrapEndpointUrl);
            System.out.println("    token_endpoint: " + tokenEndpointUrl);

            if (bootstrapEndpointUrl == null) {
                System.err.println("ERROR: No bootstrap_endpoint in PS metadata. Is bootstrap enabled?");
                return;
            }

            // Step 1a: POST /aauth/bootstrap (hwk scheme)
            String bootstrapBody = MAPPER.writeValueAsString(Map.of(
                    "agent_server", agentBaseUrl));
            byte[] bootstrapBytes = bootstrapBody.getBytes(StandardCharsets.UTF_8);

            URI bootstrapUri = URI.create(bootstrapEndpointUrl);
            String bootstrapAuthority = bootstrapUri.getHost() + ":" + bootstrapUri.getPort();
            String bootstrapPath = bootstrapUri.getPath();

            Map<String, String> hwkSigHeaders = TestSignatureBuilder.signPost(
                    "POST", bootstrapAuthority, bootstrapPath,
                    "application/json", bootstrapBytes, ephemeralKeyPair);

            var bootstrapReqBuilder = HttpRequest.newBuilder()
                    .uri(bootstrapUri)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bootstrapBytes));
            hwkSigHeaders.forEach(bootstrapReqBuilder::header);

            System.out.println("[2] POST " + bootstrapEndpointUrl + " (hwk scheme)");
            var bootstrapResp = HTTP.send(bootstrapReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("    Status: " + bootstrapResp.statusCode());

            if (bootstrapResp.statusCode() != 202) {
                System.err.println("ERROR: Expected 202, got " + bootstrapResp.statusCode());
                printBody(bootstrapResp.body());
                return;
            }

            String pendingUrl = bootstrapResp.headers().firstValue("Location").orElse(null);
            String requirement = bootstrapResp.headers().firstValue("AAuth-Requirement").orElse("");
            String interactUrl = parseParam(requirement, "url");
            String interactionCode = parseParam(requirement, "code");

            System.out.println();
            System.out.println("  ┌──────────────────────────────────────────────────────┐");
            System.out.println("  │  Open this URL to approve the bootstrap:             │");
            System.out.println("  │                                                      │");
            if (interactUrl != null && interactionCode != null) {
                String fullUrl = interactUrl + "?code=" + interactionCode;
                System.out.println("  │  " + fullUrl);
            }
            System.out.println("  │                                                      │");
            System.out.println("  └──────────────────────────────────────────────────────┘");
            System.out.println();

            // Step 1b: Poll for bootstrap_token
            System.out.println("[3] Polling " + pendingUrl + "...");
            String bootstrapToken = pollForBootstrapToken(pendingUrl, ephemeralKeyPair);

            if (bootstrapToken == null) {
                System.err.println("ERROR: Did not receive bootstrap_token");
                return;
            }

            System.out.println();
            System.out.println("=== BOOTSTRAP TOKEN RECEIVED ===");
            decodeAndPrintJwt("bootstrap_token", bootstrapToken);

            // ============================================================
            // PHASE 2: SIMULATE AGENT SERVER (build aa-agent+jwt)
            // ============================================================
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  PHASE 2: AGENT SERVER (simulated)           ║");
            System.out.println("╚══════════════════════════════════════════════╝");

            // In a real flow, the CLI would POST the bootstrap_token to the Agent Server's
            // /bootstrap endpoint. The Agent Server validates it, creates a binding, and
            // returns an aa-agent+jwt. Here we simulate that by building the token directly.

            String agentIdentifier = "aauth:botdavid@" + URI.create(agentBaseUrl).getHost();
            // Real bootstrap-onboarded agents carry ps = bootstrap_token.iss in their agent_token;
            // the announcement endpoint enforces this match.
            String agentToken = TestAgentTokenBuilder.buildAgentTokenWithPs(
                    issuerKeyPair, AGENT_KID, agentBaseUrl, agentIdentifier,
                    psIssuerUrl, ephemeralKeyPair.getPublic(), 3600);
            System.out.println("[4] Agent token built (simulated Agent Server response)");
            System.out.println("    iss=" + agentBaseUrl);
            System.out.println("    sub=" + agentIdentifier);
            decodeAndPrintJwt("agent_token", agentToken);

            // ============================================================
            // PHASE 3: BOOTSTRAP COMPLETION ANNOUNCEMENT
            // ============================================================
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  PHASE 3: BOOTSTRAP COMPLETION              ║");
            System.out.println("╚══════════════════════════════════════════════╝");

            // POST /aauth/bootstrap with jwt scheme (agent_token), empty body
            Map<String, String> jwtSigHeaders = TestSignatureBuilder.signPostJwt(
                    "POST", bootstrapAuthority, bootstrapPath,
                    "application/json", new byte[0],
                    ephemeralKeyPair, agentToken);

            var announceBuilder = HttpRequest.newBuilder()
                    .uri(bootstrapUri)
                    .POST(HttpRequest.BodyPublishers.noBody());
            jwtSigHeaders.forEach(announceBuilder::header);

            System.out.println("[5] POST " + bootstrapEndpointUrl + " (jwt scheme, empty body — announcement)");
            var announceResp = HTTP.send(announceBuilder.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("    Status: " + announceResp.statusCode());

            if (announceResp.statusCode() == 204) {
                System.out.println("    Bootstrap binding recorded at PS!");
            } else {
                System.out.println("    WARNING: Expected 204, got " + announceResp.statusCode());
                printBody(announceResp.body());
            }

            // ============================================================
            // PHASE 4: AUTHORIZATION (use agent token to get auth token)
            // ============================================================
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  PHASE 4: RESOURCE AUTHORIZATION             ║");
            System.out.println("╚══════════════════════════════════════════════╝");

            // Issue resource token (simulating the resource server challenge)
            String resourceToken = resourceServer.issueResourceToken(
                    psIssuerUrl, agentIdentifier, ephemeralThumbprint, "read write");
            System.out.println("[6] Resource token issued (scope=read write)");

            // POST /aauth/token with jwt scheme
            String tokenBody = MAPPER.writeValueAsString(Map.of(
                    "resource_token", resourceToken,
                    "justification", "I need to **read** and **write** your calendar"));
            byte[] tokenBytes = tokenBody.getBytes(StandardCharsets.UTF_8);

            URI tokenUri = URI.create(tokenEndpointUrl);
            String tokenAuthority = tokenUri.getHost() + ":" + tokenUri.getPort();

            String freshAgentToken = TestAgentTokenBuilder.buildAgentTokenWithPs(
                    issuerKeyPair, AGENT_KID, agentBaseUrl, agentIdentifier,
                    psIssuerUrl, ephemeralKeyPair.getPublic(), 3600);

            Map<String, String> tokenSigHeaders = TestSignatureBuilder.signPostJwt(
                    "POST", tokenAuthority, tokenUri.getPath(),
                    "application/json", tokenBytes, ephemeralKeyPair, freshAgentToken);

            var tokenReqBuilder = HttpRequest.newBuilder()
                    .uri(tokenUri)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(tokenBytes))
                    .header("AAuth-Capabilities", "interaction, clarification");
            tokenSigHeaders.forEach(tokenReqBuilder::header);

            System.out.println("[7] POST " + tokenEndpointUrl + " (jwt scheme)");
            var tokenResp = HTTP.send(tokenReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("    Status: " + tokenResp.statusCode());

            if (tokenResp.statusCode() == 200) {
                // Immediate auth_token (consent already cached)
                System.out.println();
                System.out.println("=== AUTH TOKEN (immediate — consent cached!) ===");
                @SuppressWarnings("unchecked")
                var respMap = MAPPER.readValue(tokenResp.body(), Map.class);
                decodeAndPrintJwt("auth_token", (String) respMap.get("auth_token"));
            } else if (tokenResp.statusCode() == 202) {
                // Deferred — need user consent
                String authPendingUrl = tokenResp.headers().firstValue("Location").orElse(null);
                String authReq = tokenResp.headers().firstValue("AAuth-Requirement").orElse("");
                String authInteractUrl = parseParam(authReq, "url");
                String authCode = parseParam(authReq, "code");

                System.out.println();
                System.out.println("  ┌──────────────────────────────────────────────────────┐");
                System.out.println("  │  Open this URL to authorize the resource access:     │");
                System.out.println("  │                                                      │");
                if (authInteractUrl != null && authCode != null) {
                    System.out.println("  │  " + authInteractUrl + "?code=" + authCode);
                }
                System.out.println("  │                                                      │");
                System.out.println("  └──────────────────────────────────────────────────────┘");
                System.out.println();

                System.out.println("[8] Polling " + authPendingUrl + "...");
                String authToken = pollForAuthToken(authPendingUrl, ephemeralKeyPair,
                        issuerKeyPair, agentBaseUrl, agentIdentifier, psIssuerUrl);

                if (authToken != null) {
                    System.out.println();
                    System.out.println("=== AUTH TOKEN RECEIVED ===");
                    decodeAndPrintJwt("auth_token", authToken);
                }
            } else {
                System.err.println("ERROR: Expected 200 or 202, got " + tokenResp.statusCode());
                printBody(tokenResp.body());
            }

            System.out.println();
            System.out.println("=== TEST COMPLETE ===");

        } finally {
            resourceServer.stop();
            agentServer.stop();
            System.out.println("Mock servers stopped.");
        }
    }

    // ---- Polling helpers ----

    private static String pollForBootstrapToken(String pendingUrl, KeyPair ephemeralKeyPair) throws Exception {
        long start = System.currentTimeMillis();
        int interval = POLL_INTERVAL;
        int count = 0;

        while ((System.currentTimeMillis() - start) / 1000 < MAX_POLL_SECONDS) {
            Thread.sleep(interval * 1000L);
            count++;

            URI uri = URI.create(pendingUrl);
            String authority = uri.getHost() + ":" + uri.getPort();
            Map<String, String> sigHeaders = TestSignatureBuilder.signGet(
                    "GET", authority, uri.getPath(), ephemeralKeyPair);

            var req = HttpRequest.newBuilder().uri(uri).GET();
            sigHeaders.forEach(req::header);

            var resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                System.out.println("    Poll #" + count + " -> 200 OK");
                @SuppressWarnings("unchecked")
                var body = MAPPER.readValue(resp.body(), Map.class);
                return (String) body.get("bootstrap_token");
            } else if (resp.statusCode() == 202) {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                System.out.println("    Poll #" + count + " -> 202 [" + elapsed + "s]");
            } else if (resp.statusCode() == 429) {
                interval = Math.min(interval + 5, 30);
                System.out.println("    Poll #" + count + " -> 429 (interval=" + interval + "s)");
            } else {
                System.out.println("    Poll #" + count + " -> " + resp.statusCode() + " (terminal)");
                printBody(resp.body());
                return null;
            }
        }
        System.out.println("    Timeout.");
        return null;
    }

    private static String pollForAuthToken(String pendingUrl, KeyPair ephemeralKeyPair,
                                            KeyPair issuerKeyPair, String agentBaseUrl,
                                            String agentIdentifier, String psIssuerUrl) throws Exception {
        long start = System.currentTimeMillis();
        int interval = POLL_INTERVAL;
        int count = 0;

        while ((System.currentTimeMillis() - start) / 1000 < MAX_POLL_SECONDS) {
            Thread.sleep(interval * 1000L);
            count++;

            URI uri = URI.create(pendingUrl);
            String authority = uri.getHost() + ":" + uri.getPort();

            String pollAgentToken = TestAgentTokenBuilder.buildAgentTokenWithPs(
                    issuerKeyPair, AGENT_KID, agentBaseUrl, agentIdentifier,
                    psIssuerUrl, ephemeralKeyPair.getPublic(), 3600);
            Map<String, String> sigHeaders = TestSignatureBuilder.signGetJwt(
                    "GET", authority, uri.getPath(), ephemeralKeyPair, pollAgentToken);

            var req = HttpRequest.newBuilder().uri(uri).GET();
            sigHeaders.forEach(req::header);

            var resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                System.out.println("    Poll #" + count + " -> 200 OK");
                @SuppressWarnings("unchecked")
                var body = MAPPER.readValue(resp.body(), Map.class);
                return (String) body.get("auth_token");
            } else if (resp.statusCode() == 202) {
                @SuppressWarnings("unchecked")
                var body = MAPPER.readValue(resp.body(), Map.class);
                String clarification = (String) body.get("clarification");
                String reqHeader = resp.headers().firstValue("AAuth-Requirement").orElse("");

                if (clarification != null && reqHeader.contains("requirement=clarification")) {
                    System.out.println("    Poll #" + count + " -> 202 CLARIFICATION");
                    System.out.println("    Question: " + clarification);

                    String answer = "I need **read** access to check your calendar and **write** access to create events.";
                    System.out.println("    Auto-response: " + answer);

                    String respJson = MAPPER.writeValueAsString(Map.of("clarification_response", answer));
                    byte[] respBytes = respJson.getBytes(StandardCharsets.UTF_8);
                    String postToken = TestAgentTokenBuilder.buildAgentTokenWithPs(
                            issuerKeyPair, AGENT_KID, agentBaseUrl, agentIdentifier,
                            psIssuerUrl, ephemeralKeyPair.getPublic(), 3600);
                    Map<String, String> postSig = TestSignatureBuilder.signPostJwt(
                            "POST", authority, uri.getPath(),
                            "application/json", respBytes, ephemeralKeyPair, postToken);

                    var postReq = HttpRequest.newBuilder().uri(uri)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(respBytes));
                    postSig.forEach(postReq::header);
                    var postResp = HTTP.send(postReq.build(), HttpResponse.BodyHandlers.ofString());
                    System.out.println("    Clarification sent -> " + postResp.statusCode());
                } else {
                    long elapsed = (System.currentTimeMillis() - start) / 1000;
                    System.out.println("    Poll #" + count + " -> 202 (" + body.get("status") + ") [" + elapsed + "s]");
                }
            } else if (resp.statusCode() == 429) {
                interval = Math.min(interval + 5, 30);
                System.out.println("    Poll #" + count + " -> 429 (interval=" + interval + "s)");
            } else {
                System.out.println("    Poll #" + count + " -> " + resp.statusCode() + " (terminal)");
                printBody(resp.body());
                return null;
            }
        }
        System.out.println("    Timeout.");
        return null;
    }

    // ---- Utilities ----

    private static HttpResponse<String> httpGet(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String extractEd25519X(KeyPair kp) {
        byte[] raw = new byte[32];
        byte[] encoded = kp.getPublic().getEncoded();
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String computeJwkThumbprint(String x) throws Exception {
        String canonical = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + x + "\"}";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static String parseParam(String header, String paramName) {
        Pattern pattern = Pattern.compile(paramName + "=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void printBody(String body) {
        try {
            Object parsed = MAPPER.readValue(body, Object.class);
            System.out.println("    " + MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(parsed).replace("\n", "\n    "));
        } catch (Exception e) {
            System.out.println("    " + body);
        }
    }

    private static void decodeAndPrintJwt(String label, String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                System.out.println("  " + label + " header: " + prettyJson(header));
                System.out.println("  " + label + " claims: " + prettyJson(claims));
            }
        } catch (Exception e) {
            System.out.println("  (Failed to decode " + label + ": " + e.getMessage() + ")");
        }
    }

    private static String prettyJson(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(MAPPER.readValue(json, Object.class))
                    .replace("\n", "\n    ");
        } catch (Exception e) {
            return json;
        }
    }
}
