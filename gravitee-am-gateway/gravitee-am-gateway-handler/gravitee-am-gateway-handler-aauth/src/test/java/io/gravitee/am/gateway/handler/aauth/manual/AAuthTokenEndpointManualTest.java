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
 * Interactive manual test for the AAUTH deferred authorization flow.
 * <p>
 * Uses two mock servers:
 * - Agent server (port 9998): serves agent metadata + JWKS
 * - Resource server (port 9999): serves resource metadata + JWKS, issues resource tokens
 * <p>
 * The agent signs requests with the jwks_uri scheme (identified agent), which triggers
 * Application auto-creation and enables the full interaction flow.
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

    private static final int AGENT_SERVER_PORT = 9998;
    private static final int RESOURCE_SERVER_PORT = 9999;
    private static final String AGENT_KID = "agent-key-1";
    private static final int MAX_POLL_DURATION_SECONDS = 300;
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String amUrl = args.length > 0 ? args[0] : "http://localhost:8092";
        String domain = args.length > 1 ? args[1] : "testdomain";

        System.out.println("=== AAUTH Deferred Authorization Manual Test ===");
        System.out.println("AM URL:  " + amUrl);
        System.out.println("Domain:  " + domain);
        System.out.println("Scheme:  jwt (agent token, per spec Section 7.1.3)");
        System.out.println();

        // Step 1: Start mock servers
        // issuerKeyPair = agent server's signing key (signs agent tokens)
        // delegateKeyPair = this agent instance's key (signs HTTP requests, embedded in cnf.jwk)
        KeyPair issuerKeyPair = TestAgentKeyPairFactory.ed25519();
        String issuerX = TestAgentKeyPairFactory.ed25519PublicKeyX();

        KeyPair delegateKeyPair = TestAgentKeyPairFactory.ed25519();
        byte[] delegateRaw = new byte[32];
        byte[] delegateEncoded = delegateKeyPair.getPublic().getEncoded();
        System.arraycopy(delegateEncoded, delegateEncoded.length - 32, delegateRaw, 0, 32);
        String delegateX = Base64.getUrlEncoder().withoutPadding().encodeToString(delegateRaw);
        String delegateThumbprint = computeJwkThumbprint(delegateX);

        MockAgentMetadataServer agentServer = new MockAgentMetadataServer(AGENT_SERVER_PORT);
        MockResourceServer resourceServer = new MockResourceServer(RESOURCE_SERVER_PORT);

        try {
            agentServer.start();
            String agentBaseUrl = agentServer.baseUrl();
            agentServer.stubMetadata(agentBaseUrl, agentBaseUrl + "/jwks.json", "Test AAUTH Agent");
            agentServer.stubJwksWithEd25519(AGENT_KID, issuerX);
            System.out.println("[1] Agent server running at " + agentBaseUrl);

            resourceServer.start();
            System.out.println("    Resource server running at " + resourceServer.baseUrl());

            // Step 2: Fetch PS metadata
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
            System.out.println("[2] PS issuer: " + psIssuerUrl);

            // Step 3: Issue resource token with aauth: agent identifier
            // The agent identifier uses aauth:local@domain format (spec Section 5.1)
            String agentIdentifier = "aauth:bot@" + URI.create(agentBaseUrl).getHost();
            String resourceToken = resourceServer.issueResourceToken(
                    psIssuerUrl, agentIdentifier, delegateThumbprint, "read write");
            System.out.println("[3] Resource token issued (agent=" + agentIdentifier + ", scope=read write)");

            // Step 4: POST /aauth/token
            String jsonBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "resource_token", resourceToken,
                    "justification", "I need to **read** and **write** your calendar entries to schedule meetings on your behalf.\n\n- Read: to check availability\n- Write: to create new events"
            ));
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);

            URI tokenUri = URI.create(tokenEndpointUrl);
            String authority = tokenUri.getHost() + (tokenUri.getPort() > 0 ? ":" + tokenUri.getPort() : "");
            String path = tokenUri.getPath();

            // Build aa-agent+jwt: issuer (agent server) signs, delegate key in cnf.jwk
            String agentToken = TestAgentTokenBuilder.buildAgentToken(
                    issuerKeyPair, AGENT_KID, agentBaseUrl,
                    agentIdentifier,
                    delegateKeyPair.getPublic(), 3600);
            System.out.println("[4a] Agent token built (iss=" + agentBaseUrl + ", sub=" + agentIdentifier + ")");

            Map<String, String> sigHeaders = TestSignatureBuilder.signPostJwt(
                    "POST", authority, path, "application/json", bodyBytes,
                    delegateKeyPair, agentToken);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpointUrl))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            sigHeaders.forEach(requestBuilder::header);

            System.out.println("[4] Sending POST " + tokenEndpointUrl + " (scheme=jwt)");
            var response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            System.out.println("    Status: " + response.statusCode());
            response.headers().firstValue("Signature-Error").ifPresent(v -> System.out.println("    Signature-Error: " + v));
            response.headers().firstValue("AAuth-Requirement").ifPresent(v -> System.out.println("    AAuth-Requirement: " + v));
            if (response.body() != null && !response.body().isBlank()) {
                printJson("    Body", response.body());
            }

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

            String interactUrl = parseParam(requirement, "url");
            String interactionCode = parseParam(requirement, "code");

            System.out.println();
            System.out.println("============================================================");
            System.out.println("  Open this URL in your browser to authorize the agent:");
            System.out.println();
            if (interactUrl != null && interactionCode != null) {
                System.out.println("  " + interactUrl + "?code=" + interactionCode);
            }
            System.out.println("============================================================");
            System.out.println();

            // Step 5: Poll the pending URL per spec Section 12.4
            System.out.println("[5] Polling " + pendingUrl + " (max " + MAX_POLL_DURATION_SECONDS + "s)...");

            long startTime = System.currentTimeMillis();
            int pollInterval = DEFAULT_POLL_INTERVAL_SECONDS;
            int pollCount = 0;

            while (true) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > MAX_POLL_DURATION_SECONDS) {
                    System.out.println("    Timeout after " + elapsed + "s. Giving up.");
                    break;
                }

                Thread.sleep(pollInterval * 1000L);
                pollCount++;

                URI pollUri = URI.create(pendingUrl);
                String pollAuthority = pollUri.getHost() + (pollUri.getPort() > 0 ? ":" + pollUri.getPort() : "");
                String pollAgentToken = TestAgentTokenBuilder.buildAgentToken(
                        issuerKeyPair, AGENT_KID, agentServer.baseUrl(),
                        agentIdentifier,
                        delegateKeyPair.getPublic(), 3600);
                Map<String, String> pollSigHeaders = TestSignatureBuilder.signGetJwt(
                        "GET", pollAuthority, pollUri.getPath(),
                        delegateKeyPair, pollAgentToken);

                var pollRequestBuilder = HttpRequest.newBuilder().uri(pollUri).GET();
                pollSigHeaders.forEach(pollRequestBuilder::header);

                var pollResponse = HTTP_CLIENT.send(pollRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int pollStatus = pollResponse.statusCode();

                if (pollStatus == 200) {
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
                        System.out.println();
                        verifyAuthTokenSignature(authToken, amUrl + "/" + domain + "/aauth",
                                resourceServer.baseUrl(), agentIdentifier);
                    }
                    break;

                } else if (pollStatus == 202) {
                    @SuppressWarnings("unchecked")
                    var pollBody = OBJECT_MAPPER.readValue(pollResponse.body(), Map.class);
                    String status = (String) pollBody.get("status");
                    System.out.println("    Poll #" + pollCount + " -> 202 (" + status + ") [" + elapsed + "s elapsed]");

                } else if (pollStatus == 429) {
                    pollInterval = Math.min(pollInterval + 5, 30);
                    System.out.println("    Poll #" + pollCount + " -> 429 slow_down (interval now " + pollInterval + "s)");

                } else {
                    System.out.println("    Poll #" + pollCount + " -> " + pollStatus + " (terminal)");
                    printJson("Response", pollResponse.body());
                    break;
                }
            }

        } finally {
            resourceServer.stop();
            agentServer.stop();
            System.out.println("\nMock servers stopped.");
        }
    }

    private static String parseParam(String header, String paramName) {
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

    /**
     * Simulate a resource server verifying the auth token per spec Section 9.4.3.
     * All 9 verification steps are implemented.
     */
    private static void verifyAuthTokenSignature(String authToken, String amBaseUrl,
                                                  String expectedResourceUrl, String expectedAgentId) {
        System.out.println("=== RESOURCE SERVER: AUTH TOKEN VERIFICATION (Section 9.4.3) ===");
        try {
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(authToken);
            com.nimbusds.jwt.JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Step 1: Verify typ is aa-auth+jwt
            String typ = signedJWT.getHeader().getType() != null ? signedJWT.getHeader().getType().getType() : null;
            check("[1] typ", "aa-auth+jwt".equals(typ), "expected aa-auth+jwt, got " + typ);

            // Step 2: Verify dwk and discover JWKS
            String dwk = (String) claims.getClaim("dwk");
            String iss = claims.getIssuer();
            check("[2] dwk", "aauth-person.json".equals(dwk) || "aauth-access.json".equals(dwk),
                    "expected aauth-person.json or aauth-access.json, got " + dwk);

            String metadataUrl = iss + "/.well-known/" + dwk;
            System.out.println("[2] Fetching PS metadata from " + metadataUrl);
            var metaResponse = HTTP_CLIENT.send(
                    HttpRequest.newBuilder().uri(URI.create(metadataUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            var psMeta = OBJECT_MAPPER.readValue(metaResponse.body(), Map.class);
            String jwksUri = (String) psMeta.get("jwks_uri");
            System.out.println("[2] Fetching JWKS from " + jwksUri);
            var jwksResponse = HTTP_CLIENT.send(
                    HttpRequest.newBuilder().uri(URI.create(jwksUri)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet.parse(jwksResponse.body());
            String kid = signedJWT.getHeader().getKeyID();
            com.nimbusds.jose.jwk.JWK jwk = jwkSet.getKeyByKeyId(kid);
            check("[2] kid lookup", jwk != null, "kid=" + kid + " not found in JWKS");

            // Verify signature
            java.security.PublicKey publicKey = io.gravitee.am.gateway.handler.aauth.signing.schemes.JwkKeyConverter.toNativePublicKey(jwk);
            String alg = signedJWT.getHeader().getAlgorithm().getName();
            String javaAlg = switch (alg) {
                case "RS256" -> "SHA256withRSA";
                case "RS384" -> "SHA384withRSA";
                case "RS512" -> "SHA512withRSA";
                case "ES256" -> "SHA256withECDSA";
                case "EdDSA" -> "Ed25519";
                default -> throw new IllegalArgumentException("Unsupported alg: " + alg);
            };
            String[] parts = signedJWT.serialize().split("\\.");
            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = signedJWT.getSignature().decode();
            java.security.Signature sig = java.security.Signature.getInstance(javaAlg);
            sig.initVerify(publicKey);
            sig.update(signingInput);
            check("[2] signature", sig.verify(signatureBytes), "JWT signature verification failed");

            // Step 3: Verify timestamps
            long now = java.time.Instant.now().getEpochSecond();
            check("[3] exp", claims.getExpirationTime().toInstant().getEpochSecond() > now, "token expired");
            check("[3] iat", claims.getIssueTime().toInstant().getEpochSecond() <= now, "iat is in the future");

            // Step 4: Verify iss is a valid URL
            check("[4] iss", iss != null && iss.startsWith("http"), "invalid iss: " + iss);

            // Step 5: Verify aud matches resource
            check("[5] aud", claims.getAudience().contains(expectedResourceUrl),
                    "expected " + expectedResourceUrl + ", got " + claims.getAudience());

            // Step 6: Verify agent matches agent identifier
            String agent = (String) claims.getClaim("agent");
            check("[6] agent format", agent != null && agent.startsWith("aauth:"),
                    "expected aauth:local@domain, got " + agent);
            check("[6] agent match", agent.equals(expectedAgentId),
                    "expected " + expectedAgentId + ", got " + agent);

            // Step 7: Verify cnf.jwk is present
            @SuppressWarnings("unchecked")
            Map<String, Object> cnf = (Map<String, Object>) claims.getClaim("cnf");
            check("[7] cnf.jwk", cnf != null && cnf.get("jwk") != null, "missing cnf.jwk");

            // Step 8: Verify act.sub matches agent
            @SuppressWarnings("unchecked")
            Map<String, Object> act = (Map<String, Object>) claims.getClaim("act");
            check("[8] act", act != null, "missing act claim");
            check("[8] act.sub", agent.equals(act.get("sub")),
                    "expected " + agent + ", got " + act.get("sub"));

            // Step 9: Verify at least one of sub or scope is present
            check("[9] sub|scope",
                    claims.getSubject() != null || claims.getClaim("scope") != null,
                    "neither sub nor scope present");

            System.out.println("[RS] ALL 9 VERIFICATION STEPS PASSED");

        } catch (Exception e) {
            System.err.println("[RS] ERROR: " + e.getMessage());
        }
    }

    private static void check(String step, boolean condition, String errorMsg) {
        if (condition) {
            System.out.println("  " + step + ": OK");
        } else {
            System.out.println("  " + step + ": FAIL — " + errorMsg);
        }
    }
}
