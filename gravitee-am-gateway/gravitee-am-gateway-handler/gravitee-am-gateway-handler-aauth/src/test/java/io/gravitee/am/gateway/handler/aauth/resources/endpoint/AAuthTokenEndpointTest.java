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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.aauth.model.AAuthTokenRequest;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenClaims;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenException;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenValidator;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Date;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AAuthTokenEndpoint} — the 202 deferred authorization branch.
 */
public class AAuthTokenEndpointTest extends RxWebTestBase {

    private static final String TOKEN_PATH = "/aauth/token";
    private ResourceTokenValidator resourceTokenValidator;
    private AAuthTokenService tokenService;
    private AAuthPendingRequestService pendingService;
    private KeyPair agentKeyPair;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        agentKeyPair = TestAgentKeyPairFactory.ed25519();
        resourceTokenValidator = mock(ResourceTokenValidator.class);
        tokenService = mock(AAuthTokenService.class);
        pendingService = mock(AAuthPendingRequestService.class);

        var endpoint = new AAuthTokenEndpoint(resourceTokenValidator, tokenService,
                pendingService, "domain-1", 600);

        router.route(TOKEN_PATH)
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create())
                .handler(ctx -> {
                    ctx.put(CONTEXT_PATH, "/testdomain");
                    ctx.put(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY,
                            new VerificationResult("jwt", "sig", agentKeyPair.getPublic(), "thumbprint", "https://agent.example", "aauth:bot@agent.example"));
                    ctx.put(AAuthTokenRequestParseHandler.AAUTH_TOKEN_REQUEST_CONTEXT_KEY,
                            new AAuthTokenRequest("resource.token.jwt", null, null, null, null, null));
                    // Set a mock Application so the guard passes
                    var app = new io.gravitee.am.model.Application();
                    app.setId("app-1");
                    app.setName("Test Agent");
                    ctx.put(io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthAgentResolveHandler.AAUTH_APPLICATION_CONTEXT_KEY, app);
                    ctx.next();
                })
                .handler(endpoint);
    }

    @Test
    public void shouldReturn202_withLocationAndRequirement() throws Exception {
        stubValidResourceToken();
        stubPendingCreation();

        var result = postToken();

        assertEquals(202, result.statusCode);
        var body = new JsonObject(result.body);
        assertEquals("pending", body.getString("status"));
        assertNotNull(result.location);
        assertTrue(result.location.contains("/aauth/pending/"));
        assertNotNull(result.aauthRequirement);
        assertTrue(result.aauthRequirement.contains("requirement=interaction"));
        assertTrue(result.aauthRequirement.contains("code="));
    }

    @Test
    public void shouldIncludeCacheControlNoStore() throws Exception {
        stubValidResourceToken();
        stubPendingCreation();

        var result = postToken();
        assertEquals("no-store", result.cacheControl);
    }

    @Test
    public void shouldIncludeRetryAfterZero() throws Exception {
        stubValidResourceToken();
        stubPendingCreation();

        var result = postToken();
        assertEquals("0", result.retryAfter);
    }

    @Test
    public void shouldReturn400_whenResourceTokenInvalid() throws Exception {
        try {
            when(resourceTokenValidator.validate(anyString(), any(), anyString()))
                    .thenThrow(new ResourceTokenException("invalid_resource_token", "bad token"));
        } catch (ResourceTokenException e) {
            // won't happen in mock setup
        }

        var result = postToken();
        assertEquals(400, result.statusCode);
        var body = new JsonObject(result.body);
        assertEquals("invalid_resource_token", body.getString("error"));
    }

    @Test
    public void shouldReturn500_whenPendingCreationFails() throws Exception {
        stubValidResourceToken();
        when(pendingService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                .thenReturn(Single.error(new RuntimeException("DB error")));

        var result = postToken();
        assertEquals(500, result.statusCode);
    }

    private void stubValidResourceToken() {
        try {
            when(resourceTokenValidator.validate(anyString(), any(), anyString()))
                    .thenReturn(new ResourceTokenClaims("https://resource.example",
                            "https://ps.example/aauth", "jti-1", "https://agent.example",
                            "thumbprint", "read write", System.currentTimeMillis() / 1000,
                            System.currentTimeMillis() / 1000 + 300));
        } catch (ResourceTokenException e) {
            // won't happen in mock setup
        }
    }

    private void stubPendingCreation() {
        var pending = new AAuthPendingRequest();
        pending.setId("pending-abc123");
        pending.setInteractionCode("XKCD-4287");
        pending.setStatus("PENDING");
        pending.setCreatedAt(new Date());

        when(pendingService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                .thenReturn(Single.just(pending));
    }

    private record TokenResult(int statusCode, String body, String location,
                                String aauthRequirement, String cacheControl, String retryAfter) {}

    private TokenResult postToken() throws Exception {
        String requestBody = new JsonObject().put("resource_token", "resource.token.jwt").encode();

        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + server.actualPort() + TOKEN_PATH
        ).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        var stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        return new TokenResult(status, body,
                conn.getHeaderField("Location"),
                conn.getHeaderField("AAuth-Requirement"),
                conn.getHeaderField("Cache-Control"),
                conn.getHeaderField("Retry-After"));
    }
}
