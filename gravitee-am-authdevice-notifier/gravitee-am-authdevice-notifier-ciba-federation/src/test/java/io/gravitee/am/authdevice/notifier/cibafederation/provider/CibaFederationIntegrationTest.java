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
import io.gravitee.am.authdevice.notifier.api.model.ADStatusRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.am.authdevice.notifier.api.model.FederatedConnection;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.*;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CibaFederationIntegrationTest {

    /** Test double for a non-identity consent-relay transform (tags each entry) — proves the
     *  cross-witness still matches when the relayed payload differs from the inbound
     *  authorization_details, not just under raw pass-through. */
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

    static final FederatedConnection CONNECTION = new FederatedConnection(
            "cid", "sec", "openid profile email", "https://localhost/.well-known/openid-configuration");
    static final List<Map<String, Object>> FDX = List.of(Map.of("type", "fdx_v1.0", "consentRequest",
            Map.of("durationType", "ONE_TIME", "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));

    static WireMockServer acmeAuth; static Vertx vertx; static WebClient sharedClient;

    @BeforeAll static void up() { acmeAuth = new WireMockServer(0); acmeAuth.start(); vertx = Vertx.vertx(); sharedClient = WebClient.create(vertx); }
    @AfterAll static void down() { acmeAuth.stop(); vertx.close(); }
    @BeforeEach void reset() { acmeAuth.resetAll(); }

    private CibaClient upstreamClient() {
        return new CibaClient(sharedClient, new ProviderMetadata("http://localhost:" + acmeAuth.port() + "/",
                "http://localhost:" + acmeAuth.port() + "/bc-authorize",
                "http://localhost:" + acmeAuth.port() + "/oauth/token"), "cid", "sec", "https://api", "client_secret_post");
    }

    /** Stub discovery resolver: this suite exercises the notify -> poll chain, not discovery itself
     *  (covered by OidcDiscoveryResolverTest); the client above is already endpoint-bound. */
    private static OidcDiscoveryResolver stubResolver() {
        var r = mock(OidcDiscoveryResolver.class);
        when(r.resolve(any())).thenReturn(Single.just(new ProviderMetadata(
                "https://idp.acme.example/", "https://idp.acme.example/bc", "https://idp.acme.example/token")));
        return r;
    }

    private void stubUpstream(String scenario, String authReqId, String tokenBody) {
        acmeAuth.stubFor(post(urlEqualTo("/bc-authorize"))
                .willReturn(okJson("{\"auth_req_id\":\"" + authReqId + "\",\"expires_in\":120,\"interval\":5}")));
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario(scenario).whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"authorization_pending\"}"))
                .willSetStateTo("decided"));
        acmeAuth.stubFor(post(urlEqualTo("/oauth/token")).inScenario(scenario).whenScenarioStateIs("decided")
                .willReturn(tokenBody.contains("\"error\"")
                        ? aResponse().withStatus(400).withBody(tokenBody)
                        : okJson(tokenBody)));
    }

    private static ADNotificationRequest notification(String tid, String loginHint) {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId(tid);
        req.setState("stateJwt");
        req.setLoginHint(loginHint);
        req.setScopes(new LinkedHashSet<>(List.of("openid")));
        req.setMessage("Approve?");
        req.setAuthorizationDetails(FDX);
        req.setConnection(CONNECTION);
        return req;
    }

    /** What the gateway persists from notify() and hands back on each client poll. */
    private static ADStatusRequest persisted(String tid, Map<String, Object> extraData) {
        return new ADStatusRequest(tid, new HashMap<>(extraData), CONNECTION);
    }

    @Test
    void notify_then_polls_relay_verbatim_hint_and_approve_with_id_token() {
        String accessToken = unsignedJwt("acme|9");
        String idToken = unsignedJwt("acme|9");
        stubUpstream("raw", "R9", "{\"access_token\":\"" + accessToken + "\",\"id_token\":\"" + idToken
                + "\",\"authorization_details\":" + io.vertx.core.json.Json.encode(FDX) + "}");
        CibaClient upstream = upstreamClient();
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> upstream, stubResolver(), null, null);

        var resp = provider.notify(notification("tid9", "acme|completion-user-7")).blockingGet();
        ADStatusRequest status = persisted("tid9", resp.getExtraData());

        assertTrue(provider.checkStatus(status).blockingGet().isEmpty());
        ADUserResponse decision = provider.checkStatus(status).blockingGet().orElseThrow();

        acmeAuth.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("login_hint=acme%7Ccompletion-user-7"))
                .withRequestBody(notMatching("(?s).*iss_sub.*")));
        acmeAuth.verify(2, postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(containing("auth_req_id=R9")));
        assertTrue(decision.isValidated(), "cross-witness must match: raw relay echoes the inbound FDX payload");
        assertEquals(idToken, decision.getIdToken());
        assertEquals(accessToken, decision.getAccessToken());
    }

    @Test
    void notify_then_polls_relay_transformed_rar_and_match_cross_witness() {
        var rendered = new TaggingConsentRelayStrategy().relay(FDX, new ConsentRelayContext(null));
        stubUpstream("transform", "R10", "{\"access_token\":\"AT\",\"id_token\":\"" + unsignedJwt("acme|10")
                + "\",\"authorization_details\":" + io.vertx.core.json.Json.encode(rendered) + "}");
        CibaClient upstream = upstreamClient();
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> upstream, stubResolver(), new TaggingConsentRelayStrategy(), null);

        var resp = provider.notify(notification("tid10", "acme|completion-user-10")).blockingGet();
        ADStatusRequest status = persisted("tid10", resp.getExtraData());

        assertTrue(provider.checkStatus(status).blockingGet().isEmpty());
        assertTrue(provider.checkStatus(status).blockingGet().orElseThrow().isValidated(),
                "cross-witness must match the transformed payload that was relayed");
        acmeAuth.verify(postRequestedFor(urlEqualTo("/bc-authorize"))
                .withRequestBody(containing("relayed"))
                .withRequestBody(containing("login_hint=acme%7Ccompletion-user-10")));
    }

    @Test
    void notify_then_polls_reject_when_upstream_approves_other_authorization_details() {
        var substituted = List.of(Map.of("type", "fdx_v1.0", "consentRequest", Map.of("durationType", "PERSISTENT")));
        stubUpstream("substituted", "R12", "{\"access_token\":\"AT\",\"id_token\":\"" + unsignedJwt("acme|12")
                + "\",\"authorization_details\":" + io.vertx.core.json.Json.encode(substituted) + "}");
        CibaClient upstream = upstreamClient();
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> upstream, stubResolver(), null, null);

        var resp = provider.notify(notification("tid12", "acme|u12")).blockingGet();
        ADStatusRequest status = persisted("tid12", io.vertx.core.json.Json.decodeValue(
                io.vertx.core.json.Json.encode(resp.getExtraData()), Map.class));

        assertTrue(provider.checkStatus(status).blockingGet().isEmpty());
        assertFalse(provider.checkStatus(status).blockingGet().orElseThrow().isValidated(),
                "tokens for authorization_details other than those relayed must be rejected");
    }

    @Test
    void notify_then_polls_reject_when_upstream_denies() {
        stubUpstream("denied", "R11", "{\"error\":\"access_denied\"}");
        CibaClient upstream = upstreamClient();
        var provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> upstream, stubResolver(), null, null);

        var resp = provider.notify(notification("tid11", "acme|u11")).blockingGet();
        ADStatusRequest status = persisted("tid11", resp.getExtraData());

        assertTrue(provider.checkStatus(status).blockingGet().isEmpty());
        assertFalse(provider.checkStatus(status).blockingGet().orElseThrow().isValidated());
    }

    /** Tiny unsigned JWT: base64url({"alg":"none"}) + "." + base64url({"sub":...}) + "." (empty sig). */
    private static String unsignedJwt(String sub) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
