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

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyPair;
import java.util.Date;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AAuthPendingEndpoint} — the polling endpoint.
 */
public class AAuthPendingEndpointTest extends RxWebTestBase {

    private static final String PENDING_PATH = "/aauth/pending/";
    private AAuthPendingRequestService pendingService;
    private KeyPair agentKeyPair;
    private String agentJkt;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        agentKeyPair = TestAgentKeyPairFactory.ed25519();
        agentJkt = "test-thumbprint";

        pendingService = mock(AAuthPendingRequestService.class);

        // Stub handler that injects a VerificationResult into the context
        router.route(PENDING_PATH + ":id")
                .handler(ctx -> {
                    ctx.put(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY,
                            new VerificationResult("hwk", "sig", agentKeyPair.getPublic(), agentJkt, null, null));
                    ctx.next();
                })
                .handler(new AAuthPendingEndpoint(pendingService));
    }

    @Test
    public void shouldReturn202PendingStatus() throws Exception {
        var pending = createPending(PendingRequestStatus.PENDING);
        when(pendingService.poll(eq("abc123"), eq(agentJkt))).thenReturn(Maybe.just(pending));

        testRequest(HttpMethod.GET, PENDING_PATH + "abc123", null,
                resp -> {
                    assertEquals("202", String.valueOf(resp.statusCode()));
                    assertNotNull(resp.getHeader("Retry-After"));
                    assertNotNull(resp.getHeader("Cache-Control"));
                },
                202, "Accepted", null);
    }

    @Test
    public void shouldReturn202InteractingStatus() throws Exception {
        var pending = createPending(PendingRequestStatus.INTERACTING);
        when(pendingService.poll(eq("abc123"), eq(agentJkt))).thenReturn(Maybe.just(pending));

        var json = fetchJson("abc123");
        assertEquals(202, json.statusCode);
        assertEquals("interacting", new JsonObject(json.body).getString("status"));
    }

    @Test
    public void shouldReturn200WithAuthToken_whenCompleted() throws Exception {
        var pending = createPending(PendingRequestStatus.COMPLETED);
        pending.setAuthToken("signed.auth.token");
        pending.setAuthTokenExpiresIn(300);
        when(pendingService.poll(eq("abc123"), eq(agentJkt))).thenReturn(Maybe.just(pending));

        var json = fetchJson("abc123");
        assertEquals(200, json.statusCode);
        var body = new JsonObject(json.body);
        assertEquals("signed.auth.token", body.getString("auth_token"));
        assertEquals(300, body.getLong("expires_in").intValue());
    }

    @Test
    public void shouldReturn403_whenDenied() throws Exception {
        var pending = createPending(PendingRequestStatus.DENIED);
        when(pendingService.poll(eq("abc123"), eq(agentJkt))).thenReturn(Maybe.just(pending));

        var json = fetchJson("abc123");
        assertEquals(403, json.statusCode);
        assertEquals("denied", new JsonObject(json.body).getString("error"));
    }

    @Test
    public void shouldReturn408_whenExpired() throws Exception {
        var pending = createPending(PendingRequestStatus.EXPIRED);
        when(pendingService.poll(eq("abc123"), eq(agentJkt))).thenReturn(Maybe.just(pending));

        var json = fetchJson("abc123");
        assertEquals(408, json.statusCode);
        assertEquals("expired", new JsonObject(json.body).getString("error"));
    }

    @Test
    public void shouldReturn410_whenNotFound() throws Exception {
        when(pendingService.poll(eq("unknown"), eq(agentJkt))).thenReturn(Maybe.empty());

        var json = fetchJson("unknown");
        assertEquals(410, json.statusCode);
        assertEquals("invalid_code", new JsonObject(json.body).getString("error"));
    }

    @Test
    public void shouldReturn429_whenTooFast() throws Exception {
        when(pendingService.poll(eq("abc123"), eq(agentJkt)))
                .thenReturn(Maybe.error(new AAuthPendingRequestService.TooFastException()));

        var json = fetchJson("abc123");
        assertEquals(429, json.statusCode);
        assertEquals("slow_down", new JsonObject(json.body).getString("error"));
    }

    @Test
    public void shouldReturn403_whenAgentKeyMismatch() throws Exception {
        when(pendingService.poll(eq("abc123"), eq(agentJkt)))
                .thenReturn(Maybe.error(new SecurityException("Agent key mismatch")));

        var json = fetchJson("abc123");
        assertEquals(403, json.statusCode);
    }

    private AAuthPendingRequest createPending(PendingRequestStatus status) {
        var req = new AAuthPendingRequest();
        req.setId("abc123");
        req.setStatus(status.name());
        req.setDomain("domain-1");
        req.setAgentJkt(agentJkt);
        req.setInteractionCode("ABCD-1234");
        req.setCreatedAt(new Date());
        req.setLastAccessAt(new Date(System.currentTimeMillis() - 10_000));
        req.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        return req;
    }

    private record HttpResult(int statusCode, String body) {}

    private HttpResult fetchJson(String pendingId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + server.actualPort() + PENDING_PATH + pendingId
        ).openConnection();
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();
        var stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String body = reader.lines().collect(Collectors.joining("\n"));
            return new HttpResult(status, body);
        }
    }
}
