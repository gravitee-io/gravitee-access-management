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
    Object rar = java.util.List.of(java.util.Map.of("type", "urn:...:user-profile"));

    @BeforeAll static void up() { wm = new WireMockServer(0); wm.start(); vertx = Vertx.vertx(); webClient = WebClient.create(vertx); }
    @AfterAll static void down() { wm.stop(); vertx.close(); }

    @BeforeEach void init() { wm.resetAll(); }

    /** Stubs the well-known and returns a resolver pointed at WireMock. */
    private OidcDiscoveryResolver resolverFor() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration")).willReturn(okJson(
            "{\"backchannel_authentication_endpoint\":\"http://localhost:" + wm.port() + "/bc-authorize\","
          + "\"token_endpoint\":\"http://localhost:" + wm.port() + "/oauth/token\"}")));
        return new OidcDiscoveryResolver(webClient, 3600);
    }

    @Test
    void bc_authorize_relays_login_hint_verbatim() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R1\",\"expires_in\":120,\"interval\":5}")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", "https://api");
        client.bcAuthorize(hint, null, "openid", "Approve?", rar).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint=" + java.net.URLEncoder.encode(hint, java.nio.charset.StandardCharsets.UTF_8)))
                .withRequestBody(containing("authorization_details=")));
    }

    @Test
    void bc_authorize_relays_login_hint_token_verbatim() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R2\",\"expires_in\":120,\"interval\":5}")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);
        client.bcAuthorize(null, "eyJ.tok.en", "openid", null, null).blockingGet();
        wm.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint_token=eyJ.tok.en"))
                .withRequestBody(notMatching(".*login_hint=.*")));
    }

    @Test
    void poll_maps_pending_slowdown_token_error() {
        var resolver = resolverFor();
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);

        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}")));
        assertEquals(CibaClient.PollKind.PENDING, client.pollToken("R1").blockingGet().kind());

        wm.resetMappings();
        resolverFor(); // re-stub well-known (resolver cache will still serve from cache)
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\":\"AT\",\"authorization_details\":[{\"type\":\"x\"}]}")));
        var t = client.pollToken("R1").blockingGet();
        assertEquals(CibaClient.PollKind.TOKEN, t.kind());
        assertEquals("AT", t.accessToken());

        wm.resetMappings();
        resolverFor();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"slow_down\"}")));
        assertEquals(CibaClient.PollKind.SLOW_DOWN, client.pollToken("R1").blockingGet().kind());

        wm.resetMappings();
        resolverFor();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"access_denied\"}")));
        assertEquals(CibaClient.PollKind.ERROR, client.pollToken("R1").blockingGet().kind());
    }

    @Test
    void poll_token_surfaces_id_token() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\":\"at\",\"id_token\":\"eyJ.id.tok\",\"authorization_details\":[]}")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);
        var res = client.pollToken("R1").blockingGet();
        assertEquals(CibaClient.PollKind.TOKEN, res.kind());
        assertEquals("eyJ.id.tok", res.idToken());
    }

    @Test
    void builds_client_from_connection_fields_and_polls_against_bundle_host() {
        var resolver = resolverFor();
        var conn = new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email",
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration");
        var c = new CibaClient(webClient, resolver, conn.wellKnownUri(), conn.clientId(), conn.clientSecret(), "https://api.example");
        wm.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\":\"at\",\"id_token\":\"it\"}")));
        var res = c.pollToken("R1").blockingGet();
        assertEquals(CibaClient.PollKind.TOKEN, res.kind());
        assertEquals("at", res.accessToken());
        assertEquals("it", res.idToken());
    }

    @Test
    void bc_authorize_non_200_errors_without_leaking_upstream_detail() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":\"invalid_request\",\"error_description\":\"internal://secret-endpoint detail\"}")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);
        var ex = assertThrows(Exception.class, () -> client.bcAuthorize(hint, null, "openid", null, null).blockingGet());
        String msg = String.valueOf(ex.getMessage());
        assertFalse(msg.contains("invalid_request"), "upstream error code must not leak to the caller");
        assertFalse(msg.contains("secret-endpoint"), "upstream detail must not leak to the caller");
    }

    @Test
    void bc_authorize_empty_body_on_200_errors() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(aResponse().withStatus(200).withBody("")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);
        assertThrows(Exception.class, () -> client.bcAuthorize(hint, null, "openid", null, null).blockingGet());
    }

    @Test
    void bc_authorize_missing_auth_req_id_on_200_errors() {
        var resolver = resolverFor();
        wm.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(okJson("{\"expires_in\":120,\"interval\":5}")));
        var client = new CibaClient(webClient, resolver,
                "http://localhost:" + wm.port() + "/.well-known/openid-configuration", "cid", "sec", null);
        assertThrows(Exception.class, () -> client.bcAuthorize(hint, null, "openid", null, null).blockingGet());
    }
}
