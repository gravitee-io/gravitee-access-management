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
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class CibaFederationIntegrationTest {

    static WireMockServer auth0; static WireMockServer gateway; static Vertx vertx; static WebClient sharedClient;

    @BeforeAll static void up() { auth0 = new WireMockServer(0); gateway = new WireMockServer(0); auth0.start(); gateway.start(); vertx = Vertx.vertx(); sharedClient = WebClient.create(vertx); }
    @AfterAll static void down() { auth0.stop(); gateway.stop(); vertx.close(); }
    @BeforeEach void reset() { auth0.resetAll(); gateway.resetAll(); }

    /** Stubs the well-known on the auth0 mock and returns a discovery resolver pointing at it. */
    private OidcDiscoveryResolver resolverFor(WireMockServer srv) {
        srv.stubFor(get(urlEqualTo("/.well-known/openid-configuration")).willReturn(okJson(
            "{\"backchannel_authentication_endpoint\":\"http://localhost:" + srv.port() + "/bc-authorize\","
          + "\"token_endpoint\":\"http://localhost:" + srv.port() + "/oauth/token\"}")));
        return new OidcDiscoveryResolver(sharedClient, 3600);
    }

    private CibaClient clientFor(WireMockServer srv) {
        return new CibaClient(sharedClient, resolverFor(srv),
            "http://localhost:" + srv.port() + "/.well-known/openid-configuration", "cid", "sec", "https://api");
    }

    @Test
    void full_flow_approved() {
        auth0.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(okJson("{\"auth_req_id\":\"R1\",\"expires_in\":120,\"interval\":1}")));
        // first poll pending, then token carrying the same rendered RAR back (positive witness)
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("p").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}")).willSetStateTo("ok"));
        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(auth0);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());
        poller.seedClient("tid1", a0);

        Auth0UserProfileConsentRenderer renderer = new Auth0UserProfileConsentRenderer();
        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        var rendered = renderer.render(fdx, "Acme");

        // make Auth0 return the SAME rendered payload in the token so the witness matches; a federated
        // completion always carries an id_token (the identity assertion the notifier requires).
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("p").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"AT\",\"id_token\":\"id.tok\",\"authorization_details\":" + io.vertx.core.json.Json.encode(rendered) + "}")));

        store.put(new PendingAuthStore.Pending("tid1","stateJwt","R1",1,
                Instant.now().getEpochSecond()+120, CrossWitness.hash(rendered), cbUrl));
        // exercise the bc-authorize relay (asserted below): hint relayed verbatim, transformed RAR
        a0.bcAuthorize("auth0|u1", null, "openid", "Approve?", rendered).blockingGet();

        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid1").blockingGet());
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid1").blockingGet());

        auth0.verify(postRequestedFor(urlEqualTo("/bc-authorize")).withRequestBody(containing("authorization_details=")));
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("tid=tid1")).withRequestBody(containing("validated=true")));
        assertNull(store.get("tid1"));
    }

    /**
     * End-to-end through the PROVIDER: notify (verbatim login_hint, NO subject) -> two poll rounds
     * (pending, then token) -> gateway callback. Proves the corrected flow:
     *   1. /bc-authorize received the inbound login_hint VERBATIM (no iss_sub wrapping).
     *   2. The /cb callback carried validated=true AND the raw id_token (forwarded verbatim from the token response).
     *   3. cross_witness_match=true — the relayed authorization_details the provider sent equals what the
     *      /oauth/token stub echoed back (driven with the passthrough strategy so relayed RAR == inbound RAR,
     *      which keeps the witness echo exact and the assertion clean).
     */
    @Test
    void provider_notify_poll_callback_relays_verbatim_hint_and_carries_id_token() {
        // FDX consent in -> with passthrough relay, this exact list is what gets relayed and must be echoed back.
        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        // Passthrough: relayedRar == fdx, so the token echo below is byte-for-byte the same payload.
        var relayed = new PassthroughRelayStrategy().relay(fdx);

        // interval=3600 so the timer scheduled by notify() does not fire during the test; we drive pollOnce manually.
        auth0.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R9\",\"expires_in\":120,\"interval\":3600}")));

        // First poll -> authorization_pending; second poll -> token.
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}"))
                .willSetStateTo("ok"));
        // access_token is an unsigned JWT carrying sub=auth0|9; authorization_details echoes the relayed RAR verbatim.
        // id_token is a small unsigned JWT carrying its own sub — it is what the callback forwards (asserted below).
        String accessToken = unsignedJwt("auth0|9");
        String idToken = unsignedJwt("auth0|9");
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"id_token\":\"" + idToken + "\",\"authorization_details\":"
                        + io.vertx.core.json.Json.encode(relayed) + "}")));

        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(auth0);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());

        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                a0, new PassthroughRelayStrategy(), store, poller, vertx, 120);

        // Verbatim hint, NO subject — Gravitee is a transparent relay.
        String verbatimHint = "auth0|completion-user-7";
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid9");
        req.setState("stateJwt9");
        req.setLoginHint(verbatimHint);
        req.setScopes(new LinkedHashSet<>(List.of("openid")));
        req.setMessage("Approve?");
        req.setAuthorizationDetails(fdx);
        req.setCallbackUrl(cbUrl);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "sec", "openid profile email", "https://localhost/.well-known/openid-configuration"));

        var resp = provider.notify(req).blockingGet();
        assertEquals("tid9", resp.getTransactionId());
        // notify() stored the pre-send hash of the relayed RAR — capture it before the poll loop clears the entry.
        String storedPreSendHash = store.get("tid9").adHashPreSend();
        assertNotNull(storedPreSendHash);

        // Drive the poll loop: pending, then token.
        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid9").blockingGet());
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid9").blockingGet());

        // 1. bc-authorize carried the verbatim login_hint, URL-encoded (auth0|... -> auth0%7C...), no iss_sub wrapping.
        auth0.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint=auth0%7Ccompletion-user-7"))
                .withRequestBody(notMatching("(?s).*iss_sub.*")));

        // 2. gateway callback carried validated=true AND the id_token and access_token forwarded verbatim (URL-encoded).
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("tid=tid9"))
                .withRequestBody(containing("validated=true"))
                .withRequestBody(containing("id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8))));
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("access_token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8))));

        // 3. cross_witness_match=true — reproduce the poller's exact comparison: the pre-send hash that
        //    notify() stored vs the authorization_details the /oauth/token stub echoed back, decoded the same
        //    way pollToken() decodes it (JsonArray.getList()). With passthrough these are equal => match.
        Object echoedFromToken = new io.vertx.core.json.JsonArray(io.vertx.core.json.Json.encode(relayed)).getList();
        assertTrue(CrossWitness.matchesHash(storedPreSendHash, echoedFromToken),
                "cross_witness_match must be true: pre-send hash == hash of echoed authorization_details");

        assertNull(store.get("tid9"));
    }

    /**
     * Same provider->poll->callback chain as above, but driven with the PRODUCTION-DEFAULT
     * {@link Auth0UserProfileRelayStrategy} (the transform), not passthrough. Proves the cross-witness
     * still matches when the relayed payload is the RENDERED user-profile URN — not the raw FDX:
     *   1. /bc-authorize received the RENDERED user-profile URN (authorization_details carries the URN type)
     *      AND the inbound login_hint VERBATIM (no iss_sub wrapping).
     *   2. The /cb callback carried validated=true AND the raw id_token (forwarded verbatim from the token response).
     *   3. cross_witness_match=true — the /oauth/token stub echoes the SAME rendered URN the provider sent
     *      (computed here the same way the provider does), so the pre-send hash matches the echoed payload.
     */
    @Test
    void provider_notify_poll_callback_relays_transformed_user_profile_rar_and_matches_cross_witness() {
        String recipient = "Acme Bank";
        // FDX consent in — under the transform strategy this is rendered to a user-profile URN before relay.
        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        // Compute the rendered payload the SAME way the provider does — this is what gets relayed AND must be
        // echoed back by the token stub for the witness to match (NOT the raw fdx).
        var rendered = new Auth0UserProfileRelayStrategy(recipient).relay(fdx);

        // interval=3600 so the timer scheduled by notify() does not fire during the test; we drive pollOnce manually.
        auth0.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R10\",\"expires_in\":120,\"interval\":3600}")));

        // First poll -> authorization_pending; second poll -> token.
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e-transform").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}"))
                .willSetStateTo("ok"));
        // access_token is an unsigned JWT carrying sub=auth0|10; authorization_details echoes the RENDERED URN.
        // id_token is a small unsigned JWT carrying its own sub — it is what the callback forwards (asserted below).
        String accessToken = unsignedJwt("auth0|10");
        String idToken = unsignedJwt("auth0|10");
        auth0.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e-transform").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"id_token\":\"" + idToken + "\",\"authorization_details\":"
                        + io.vertx.core.json.Json.encode(rendered) + "}")));

        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(auth0);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());

        // PRODUCTION-DEFAULT transform strategy — relay renders FDX to the user-profile URN.
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                a0, new Auth0UserProfileRelayStrategy(recipient), store, poller, vertx, 120);

        // Verbatim hint, NO subject — Gravitee is a transparent relay.
        String verbatimHint = "auth0|completion-user-10";
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid10");
        req.setState("stateJwt10");
        req.setLoginHint(verbatimHint);
        req.setScopes(new LinkedHashSet<>(List.of("openid")));
        req.setMessage("Approve?");
        req.setAuthorizationDetails(fdx);
        req.setCallbackUrl(cbUrl);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "sec", "openid profile email", "https://localhost/.well-known/openid-configuration"));

        var resp = provider.notify(req).blockingGet();
        assertEquals("tid10", resp.getTransactionId());
        // notify() stored the pre-send hash of the RENDERED RAR — capture it before the poll loop clears the entry.
        String storedPreSendHash = store.get("tid10").adHashPreSend();
        assertNotNull(storedPreSendHash);

        // Drive the poll loop: pending, then token.
        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid10").blockingGet());
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid10").blockingGet());

        // 1. bc-authorize carried the RENDERED user-profile URN (URL-encoded "user-profile") AND the verbatim
        //    login_hint (auth0|... -> auth0%7C...), no iss_sub wrapping.
        auth0.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("authorization_details="))
                .withRequestBody(containing("user-profile"))
                .withRequestBody(containing("login_hint=auth0%7Ccompletion-user-10"))
                .withRequestBody(notMatching("(?s).*iss_sub.*")));

        // 2. gateway callback carried validated=true AND the id_token and access_token forwarded verbatim (URL-encoded).
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("tid=tid10"))
                .withRequestBody(containing("validated=true"))
                .withRequestBody(containing("id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8))));
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("access_token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8))));

        // 3. cross_witness_match=true — reproduce the poller's exact comparison: the pre-send hash that notify()
        //    stored (over the RENDERED URN) vs the authorization_details the /oauth/token stub echoed back,
        //    decoded the same way pollToken() decodes it (JsonArray.getList()). With the transform strategy these
        //    are equal only because we echoed the rendered URN — echoing the raw fdx would NOT match.
        Object echoedRenderedFromToken = new io.vertx.core.json.JsonArray(io.vertx.core.json.Json.encode(rendered)).getList();
        assertTrue(CrossWitness.matchesHash(storedPreSendHash, echoedRenderedFromToken),
                "cross_witness_match must be true: pre-send hash (of rendered URN) == hash of echoed authorization_details");

        assertNull(store.get("tid10"));
    }

    /** Tiny unsigned JWT: base64url({"alg":"none"}) + "." + base64url({"sub":...}) + "." (empty sig). */
    private static String unsignedJwt(String sub) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
