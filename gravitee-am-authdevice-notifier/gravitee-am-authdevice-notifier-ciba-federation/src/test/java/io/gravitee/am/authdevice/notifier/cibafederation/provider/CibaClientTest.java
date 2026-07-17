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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class CibaClientTest {

    static WireMockServer wm;
    static Vertx vertx;
    static WebClient webClient;
    String hint = "{\"format\":\"iss_sub\",\"iss\":\"https://op.example/\",\"sub\":\"acme|7\"}";
    Object rar = java.util.List.of(java.util.Map.of("type", "fdx_v1.0"));

    @BeforeAll static void up() { wm = new WireMockServer(0); wm.start(); vertx = Vertx.vertx(); webClient = WebClient.create(vertx); }
    @AfterAll static void down() { wm.stop(); vertx.close(); }
    @BeforeEach void init() { wm.resetAll(); }

    private ProviderMetadata meta() {
        return new ProviderMetadata("http://localhost:" + wm.port() + "/",
                "http://localhost:" + wm.port() + "/bc-authorize",
                "http://localhost:" + wm.port() + "/oauth/token");
    }
    private CibaClient client(String audience, String authMethod) {
        return new CibaClient(webClient, meta(), "cid", "sec", audience, authMethod);
    }
    private static CibaHints login(String h) { return new CibaHints(h, null, null); }

    @Test
    void bc_authorize_relays_login_hint_verbatim() {
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R1\",\"expires_in\":120,\"interval\":5}")));
        client("https://api", "client_secret_post").bcAuthorize(login(hint), "openid", "Approve?", rar).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint=" + java.net.URLEncoder.encode(hint, java.nio.charset.StandardCharsets.UTF_8)))
                .withRequestBody(containing("authorization_details="))
                .withRequestBody(containing("client_secret=sec")));
    }

    @Test
    void bc_authorize_relays_login_hint_token_verbatim() {
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R2\",\"expires_in\":120,\"interval\":5}")));
        client(null, "client_secret_post").bcAuthorize(new CibaHints(null, "eyJ.tok.en", null), "openid", null, null).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint_token=eyJ.tok.en"))
                .withRequestBody(notMatching(".*login_hint=.*")));
    }

    @Test
    void bc_authorize_forwards_id_token_hint() {
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R3\",\"expires_in\":120,\"interval\":5}")));
        client(null, "client_secret_post").bcAuthorize(new CibaHints(null, null, "eyJ.id.tok"), "openid", null, null).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("id_token_hint=eyJ.id.tok"))
                .withRequestBody(notMatching(".*login_hint=.*")));
    }

    @Test
    void poll_maps_pending_slowdown_token_error() {
        var c = client(null, "client_secret_post");
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}")));
        assertEquals(CibaClient.PollKind.PENDING, c.pollToken("R1").blockingGet().kind());

        wm.resetMappings();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\":\"AT\",\"authorization_details\":[{\"type\":\"x\"}]}")));
        var t = c.pollToken("R1").blockingGet();
        assertEquals(CibaClient.PollKind.TOKEN, t.kind());
        assertEquals("AT", t.accessToken());

        wm.resetMappings();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"slow_down\"}")));
        assertEquals(CibaClient.PollKind.SLOW_DOWN, c.pollToken("R1").blockingGet().kind());

        wm.resetMappings();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"access_denied\"}")));
        assertEquals(CibaClient.PollKind.ERROR, c.pollToken("R1").blockingGet().kind());
    }

    @Test
    void poll_token_surfaces_id_token() {
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\":\"at\",\"id_token\":\"eyJ.id.tok\",\"authorization_details\":[]}")));
        var res = client(null, "client_secret_post").pollToken("R1").blockingGet();
        assertEquals(CibaClient.PollKind.TOKEN, res.kind());
        assertEquals("eyJ.id.tok", res.idToken());
        wm.verify(postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(containing("client_secret=sec")));
    }

    @Test
    void bc_authorize_non_200_errors_without_leaking_upstream_detail() {
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":\"invalid_request\",\"error_description\":\"internal://secret-endpoint detail\"}")));
        var ex = assertThrows(Exception.class, () -> client(null, "client_secret_post").bcAuthorize(login(hint), "openid", null, null).blockingGet());
        String msg = String.valueOf(ex.getMessage());
        assertFalse(msg.contains("invalid_request"));
        assertFalse(msg.contains("secret-endpoint"));
    }

    @Test
    void bc_authorize_empty_body_on_200_errors() {
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(aResponse().withStatus(200).withBody("")));
        assertThrows(Exception.class, () -> client(null, "client_secret_post").bcAuthorize(login(hint), "openid", null, null).blockingGet());
    }

    @Test
    void bc_authorize_missing_auth_req_id_on_200_errors() {
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(okJson("{\"expires_in\":120,\"interval\":5}")));
        assertThrows(Exception.class, () -> client(null, "client_secret_post").bcAuthorize(login(hint), "openid", null, null).blockingGet());
    }

    @Test
    void basic_auth_sends_authorization_header_and_no_secret_in_body() {
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R1\",\"expires_in\":120,\"interval\":5}")));
        client(null, "client_secret_basic").bcAuthorize(login(hint), "openid", null, null).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withHeader("Authorization", matching("Basic .+"))
                .withRequestBody(containing("client_id=cid"))
                .withRequestBody(notMatching(".*client_secret=.*")));
    }

    @Test
    void poll_basic_auth_sends_authorization_header_and_no_secret_in_body() {
        wm.stubFor(post(urlEqualTo("/oauth/token")).willReturn(okJson("{\"access_token\":\"AT\"}")));
        client(null, "client_secret_basic").pollToken("R1").blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/oauth/token"))
                .withHeader("Authorization", matching("Basic .+"))
                .withRequestBody(containing("client_id=cid"))
                .withRequestBody(notMatching(".*client_secret=.*")));
    }

    @Test
    void unsupported_client_auth_method_fails_closed_at_build() {
        var ex = assertThrows(IllegalStateException.class, () -> client(null, "private_key_jwt"));
        assertTrue(String.valueOf(ex.getMessage()).contains("private_key_jwt"));
    }
}
