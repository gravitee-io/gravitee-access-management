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
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CibaFederationIntegrationTest {

    /** Test double for a non-identity consent-relay transform (tags each entry) — proves the
     *  poll/callback/cross-witness plumbing still works when the relayed payload differs from the
     *  inbound authorization_details, not just under raw pass-through. */
    static final class TaggingConsentRelayStrategy implements ConsentRelayStrategy {
        public String id() { return "test-tagging"; }
        public List<Map<String, Object>> relay(List<Map<String, Object>> ad, ConsentRelayContext ctx) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> entry : ad) {
                Map<String, Object> tagged = new LinkedHashMap<>(entry);
                tagged.put("relayed", Boolean.TRUE);
                out.add(tagged);
            }
            return out;
        }
    }

    static WireMockServer acmeAuth; static WireMockServer gateway; static Vertx vertx; static WebClient sharedClient;

    @BeforeAll static void up() { acmeAuth = new WireMockServer(0); gateway = new WireMockServer(0); acmeAuth.start(); gateway.start(); vertx = Vertx.vertx(); sharedClient = WebClient.create(vertx); }
    @AfterAll static void down() { acmeAuth.stop(); gateway.stop(); vertx.close(); }
    @BeforeEach void reset() { acmeAuth.resetAll(); gateway.resetAll(); }

    /** Provider metadata pointing at the given mock's CIBA endpoints. In the Y flow discovery is resolved
     *  once up front by the provider; the pure-transport client is bound directly to these endpoints. */
    private ProviderMetadata metaFor(WireMockServer srv) {
        return new ProviderMetadata("http://localhost:" + srv.port() + "/",
            "http://localhost:" + srv.port() + "/bc-authorize",
            "http://localhost:" + srv.port() + "/oauth/token");
    }

    private CibaClient clientFor(WireMockServer srv) {
        return new CibaClient(sharedClient, metaFor(srv), "cid", "sec", "https://api", "client_secret_post");
    }

    /** Stub discovery resolver for the provider's resolve-once step: returns fixed metadata regardless of
     *  the well-known URI. This suite exercises the notify->poll->callback chain, not discovery itself
     *  (covered by OidcDiscoveryResolverTest); the pre-built client above is already endpoint-bound. */
    private static OidcDiscoveryResolver stubResolver() {
        var r = mock(OidcDiscoveryResolver.class);
        when(r.resolve(any())).thenReturn(Single.just(new ProviderMetadata(
                "https://idp.acme.example/", "https://idp.acme.example/bc", "https://idp.acme.example/token")));
        return r;
    }

    @Test
    void full_flow_approved() {
        acmeAuth.stubFor(post(urlEqualTo("/bc-authorize")).willReturn(okJson("{\"auth_req_id\":\"R1\",\"expires_in\":120,\"interval\":1}")));
        // first poll pending, then token carrying the same rendered RAR back (positive witness)
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("p").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}")).willSetStateTo("ok"));
        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(acmeAuth);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec", "client_secret_post");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());
        poller.seedClient("tid1", a0);

        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        // Any relayed RAR exercises this poll/callback/cross-witness plumbing test; a blank consent-relay
        // strategy relays raw RAR (identity), so relayed == inbound and the witness echo stays exact.
        var rendered = fdx;

        // make acmeAuth return the SAME rendered payload in the token so the witness matches; a federated
        // completion always carries an id_token (the identity assertion the notifier requires).
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("p").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"AT\",\"id_token\":\"id.tok\",\"authorization_details\":" + io.vertx.core.json.Json.encode(rendered) + "}")));

        store.put(new PendingAuthStore.Pending("tid1","stateJwt","R1",1,
                Instant.now().getEpochSecond()+120, CrossWitness.hash(rendered), cbUrl));
        // exercise the bc-authorize relay (asserted below): hint relayed verbatim, relayed RAR
        a0.bcAuthorize(new CibaHints("acme|u1", null, null), "openid", "Approve?", rendered).blockingGet();

        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid1").blockingGet());
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid1").blockingGet());

        acmeAuth.verify(postRequestedFor(urlEqualTo("/bc-authorize")).withRequestBody(containing("authorization_details=")));
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
     *      /oauth/token stub echoed back (driven with a blank consent-relay strategy so relayed RAR ==
     *      inbound RAR, which keeps the witness echo exact and the assertion clean).
     */
    @Test
    void provider_notify_poll_callback_relays_verbatim_hint_and_carries_id_token() {
        // FDX consent in -> with a blank (raw) relay, this exact list is what gets relayed and must be echoed back.
        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        // Raw relay: relayedRar == fdx, so the token echo below is byte-for-byte the same payload.
        var relayed = fdx;

        // interval=3600 so the timer scheduled by notify() does not fire during the test; we drive pollOnce manually.
        acmeAuth.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R9\",\"expires_in\":120,\"interval\":3600}")));

        // First poll -> authorization_pending; second poll -> token.
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}"))
                .willSetStateTo("ok"));
        // access_token is an unsigned JWT carrying sub=acme|9; authorization_details echoes the relayed RAR verbatim.
        // id_token is a small unsigned JWT carrying its own sub — it is what the callback forwards (asserted below).
        String accessToken = unsignedJwt("acme|9");
        String idToken = unsignedJwt("acme|9");
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"id_token\":\"" + idToken + "\",\"authorization_details\":"
                        + io.vertx.core.json.Json.encode(relayed) + "}")));

        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(acmeAuth);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec", "client_secret_post");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());

        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> a0, stubResolver(), null, null, store, poller, vertx, 120);

        // Verbatim hint, NO subject — Gravitee is a transparent relay.
        String verbatimHint = "acme|completion-user-7";
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

        // 1. bc-authorize carried the verbatim login_hint, URL-encoded (acme|... -> acme%7C...), no iss_sub wrapping.
        acmeAuth.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint=acme%7Ccompletion-user-7"))
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
     * Same provider->poll->callback chain as above, but driven with a non-identity consent-relay
     * transform strategy ({@link TaggingConsentRelayStrategy}), not a blank/raw relay. Proves the
     * cross-witness still matches when the relayed payload is the TRANSFORMED payload — not the raw FDX:
     *   1. /bc-authorize received the TRANSFORMED payload (authorization_details carries the strategy's tag)
     *      AND the inbound login_hint VERBATIM (no iss_sub wrapping).
     *   2. The /cb callback carried validated=true AND the raw id_token (forwarded verbatim from the token response).
     *   3. cross_witness_match=true — the /oauth/token stub echoes the SAME transformed payload the provider sent
     *      (computed here the same way the provider does), so the pre-send hash matches the echoed payload.
     */
    @Test
    void provider_notify_poll_callback_relays_transformed_rar_and_matches_cross_witness() {
        // FDX consent in — under the transform strategy this is tagged before relay.
        var fdx = List.<Map<String,Object>>of(Map.of("type","fdx_v1.0","consentRequest",
                Map.of("durationType","ONE_TIME","resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        // Compute the transformed payload the SAME way the provider does — this is what gets relayed AND must be
        // echoed back by the token stub for the witness to match (NOT the raw fdx). forTest() always wires
        // ConsentRelayContext(null), so mirror that here.
        var rendered = new TaggingConsentRelayStrategy().relay(fdx, new ConsentRelayContext(null));

        // interval=3600 so the timer scheduled by notify() does not fire during the test; we drive pollOnce manually.
        acmeAuth.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"R10\",\"expires_in\":120,\"interval\":3600}")));

        // First poll -> authorization_pending; second poll -> token.
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e-transform").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}"))
                .willSetStateTo("ok"));
        // access_token is an unsigned JWT carrying sub=acme|10; authorization_details echoes the TRANSFORMED payload.
        // id_token is a small unsigned JWT carrying its own sub — it is what the callback forwards (asserted below).
        String accessToken = unsignedJwt("acme|10");
        String idToken = unsignedJwt("acme|10");
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario("e2e-transform").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"id_token\":\"" + idToken + "\",\"authorization_details\":"
                        + io.vertx.core.json.Json.encode(rendered) + "}")));

        gateway.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));

        CibaClient a0 = clientFor(acmeAuth);
        PendingAuthStore store = new PendingAuthStore();
        GatewayCallbackClient cb = new GatewayCallbackClient(sharedClient, "cbid", "cbsec", "client_secret_post");
        String cbUrl = "http://localhost:" + gateway.port() + "/cb";
        AuthorizationPoller poller = new AuthorizationPoller(cb, store, () -> Instant.now().getEpochSecond());

        // Non-identity transform strategy — relay tags each FDX entry before it is sent.
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> a0, stubResolver(), new TaggingConsentRelayStrategy(), null, store, poller, vertx, 120);

        // Verbatim hint, NO subject — Gravitee is a transparent relay.
        String verbatimHint = "acme|completion-user-10";
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
        // notify() stored the pre-send hash of the TRANSFORMED payload — capture it before the poll loop clears the entry.
        String storedPreSendHash = store.get("tid10").adHashPreSend();
        assertNotNull(storedPreSendHash);

        // Drive the poll loop: pending, then token.
        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid10").blockingGet());
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid10").blockingGet());

        // 1. bc-authorize carried the TRANSFORMED payload (its "relayed" tag) AND the verbatim
        //    login_hint (acme|... -> acme%7C...), no iss_sub wrapping.
        acmeAuth.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("authorization_details="))
                .withRequestBody(containing("relayed"))
                .withRequestBody(containing("login_hint=acme%7Ccompletion-user-10"))
                .withRequestBody(notMatching("(?s).*iss_sub.*")));

        // 2. gateway callback carried validated=true AND the id_token and access_token forwarded verbatim (URL-encoded).
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("tid=tid10"))
                .withRequestBody(containing("validated=true"))
                .withRequestBody(containing("id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8))));
        gateway.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("access_token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8))));

        // 3. cross_witness_match=true — reproduce the poller's exact comparison: the pre-send hash that notify()
        //    stored (over the TRANSFORMED payload) vs the authorization_details the /oauth/token stub echoed back,
        //    decoded the same way pollToken() decodes it (JsonArray.getList()). With the transform strategy these
        //    are equal only because we echoed the transformed payload — echoing the raw fdx would NOT match.
        Object echoedRenderedFromToken = new io.vertx.core.json.JsonArray(io.vertx.core.json.Json.encode(rendered)).getList();
        assertTrue(CrossWitness.matchesHash(storedPreSendHash, echoedRenderedFromToken),
                "cross_witness_match must be true: pre-send hash (of transformed payload) == hash of echoed authorization_details");

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
