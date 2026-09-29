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

import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADStatusRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.am.authdevice.notifier.api.model.FederatedConnection;
import io.gravitee.am.authdevice.notifier.api.model.NotifierCapability;
import io.gravitee.am.authdevice.notifier.cibafederation.CibaFederationAuthenticationDeviceNotifierConfiguration;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CibaFederationProviderNotifyTest {

    /** Public, no-arg identity double: reused wherever a construction site needs a concrete
     *  {@link ConsentRelayStrategy} but is not itself testing a transform. */
    static final class TestStrategy implements ConsentRelayStrategy {
        public String id() { return "test-strategy"; }
        public List<Map<String, Object>> relay(List<Map<String, Object>> ad, ConsentRelayContext ctx) {
            return ad; // identity; assert the wiring, not a vendor transform
        }
    }

    /** Test double for a non-identity transform (tags each entry) — used to prove notify() actually
     *  applies a configured strategy's output (not just wires it through unchanged). */
    static final class TaggingTestStrategy implements ConsentRelayStrategy {
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

    /** Non-identity hint-decoration double: wraps the login_hint using the discovery issuer, proving the
     *  provider prepares the hint with the resolved {@link ProviderMetadata} before relay. */
    static final class WrappingHintStrategy implements HintDecorationStrategy {
        public String id() { return "test-wrap"; }
        public CibaHints decorate(CibaHints in, HintDecorationContext ctx) {
            return in.withLoginHint("wrapped(" + ctx.provider().issuer() + "|" + in.loginHint() + ")");
        }
    }

    /** Stub discovery resolver: returns fixed provider metadata regardless of the well-known URI, so the
     *  Y-flow's resolve-once step succeeds without an HTTP round-trip. */
    static OidcDiscoveryResolver stubResolver() {
        var r = mock(OidcDiscoveryResolver.class);
        when(r.resolve(any())).thenReturn(Single.just(new ProviderMetadata(
                "https://idp.acme.example/", "https://idp.acme.example/bc", "https://idp.acme.example/token")));
        return r;
    }

    static final FederatedConnection CONNECTION = new FederatedConnection(
            "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration");

    CibaClient acmeAuth; ConsentRelayStrategy consentStrategy;
    CibaFederationAuthenticationDeviceNotifierProvider provider;

    @BeforeEach void init() {
        acmeAuth = mock(CibaClient.class);
        consentStrategy = null; // blank config → raw relay (no transform)
        when(acmeAuth.bcAuthorize(any(), any(), any(), any()))
                .thenReturn(Single.just(new CibaClient.BcAuthorizeResult("R1", 120, 5)));
        provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), consentStrategy, /*hintStrategy*/ null);
    }

    static ADStatusRequest statusRequest(Map<String, Object> state) {
        return new ADStatusRequest("tid1", state, CONNECTION);
    }

    @Test
    void notify_relays_hint_verbatim_and_consent_via_configured_strategy() {
        // Proves notify() threads a configured (non-blank) strategy through consentStrategy.relay(rar, ctx)
        // end-to-end (hint verbatim, consent relayed, upstream handle recorded). The raw-vs-applied seam itself
        // (blank -> unchanged, selected -> transformed) is covered by the two seam tests below, so this
        // stays a stateless identity double rather than a vendor transform.
        provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), new TestStrategy(), /*hintStrategy*/ null);
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setAuthorizationDetails(List.of(Map.of("type", "fdx_v1.0",
                "consentRequest", Map.of("durationType", "ONE_TIME",
                        "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC")))))));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email", "https://idp.acme.example/.well-known/openid-configuration"));

        var resp = provider.notify(req).blockingGet();

        assertEquals("tid1", resp.getTransactionId());
        ArgumentCaptor<CibaHints> hints = ArgumentCaptor.forClass(CibaHints.class);
        ArgumentCaptor<Object> rar = ArgumentCaptor.forClass(Object.class);
        verify(acmeAuth).bcAuthorize(hints.capture(), eq("openid profile email"), eq("Approve?"), rar.capture());
        assertEquals("acme|u1", hints.getValue().loginHint());
        assertNull(hints.getValue().loginHintToken());
        assertTrue(CrossWitness.canonical(rar.getValue()).contains("fdx_v1.0"));
        assertEquals("R1", resp.getExtraData().get(CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID));
        assertEquals(CrossWitness.hash(rar.getValue()),
                resp.getExtraData().get(CibaFederationAuthenticationDeviceNotifierProvider.RELAYED_AD_HASH));
    }

    /** Seam case 1/2 — blank strategy (as wired by {@code init()}: consentStrategy == null) relays
     *  authorization_details unchanged (raw RAR), byte-for-byte identical to the input. */
    @Test
    void blank_strategy_relays_authorization_details_unchanged() {
        var inputRar = List.of(Map.<String, Object>of("type", "fdx_v1.0",
                "consentRequest", Map.of("durationType", "ONE_TIME",
                        "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidBlank"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setAuthorizationDetails(inputRar);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));

        provider.notify(req).blockingGet(); // provider from init(): consentStrategy == null

        ArgumentCaptor<Object> rar = ArgumentCaptor.forClass(Object.class);
        verify(acmeAuth).bcAuthorize(any(), any(), any(), rar.capture());
        assertEquals(inputRar, rar.getValue(), "blank strategy must relay authorization_details unchanged");
    }

    /** Seam case 2/2 — a selected (non-blank) strategy's transform is actually applied to the relayed
     *  payload, not bypassed. */
    @Test
    void selected_strategy_transforms_before_relay() {
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), new TaggingTestStrategy(), /*hintStrategy*/ null);
        var inputRar = List.of(Map.<String, Object>of("type", "fdx_v1.0",
                "consentRequest", Map.of("durationType", "ONE_TIME",
                        "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidApplied"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setAuthorizationDetails(inputRar);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));

        p.notify(req).blockingGet();

        ArgumentCaptor<Object> rar = ArgumentCaptor.forClass(Object.class);
        verify(acmeAuth).bcAuthorize(any(), any(), any(), rar.capture());
        @SuppressWarnings("unchecked")
        var relayed = (List<Map<String, Object>>) rar.getValue();
        assertNotEquals(inputRar, relayed, "selected strategy's transform must actually change the relayed payload");
        assertEquals(Boolean.TRUE, relayed.get(0).get("relayed"), "relayed payload must carry the strategy's tag");
    }

    @Test
    void notify_propagates_bcauthorize_error() {
        when(acmeAuth.bcAuthorize(any(), any(), any(), any()))
                .thenReturn(io.reactivex.rxjava3.core.Single.error(new IllegalStateException("bc-authorize failed: invalid_client")));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidX"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(java.util.Set.of("openid"));
        req.setAuthorizationDetails(java.util.List.of(java.util.Map.of("type", "fdx_v1.0",
                "consentRequest", java.util.Map.of("durationType", "ONE_TIME",
                        "resources", java.util.List.of(java.util.Map.of("dataClusters", java.util.List.of("ACCOUNT_BASIC")))))));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email", "https://idp.acme.example/.well-known/openid-configuration"));

        assertThrows(Exception.class, () -> provider.notify(req).blockingGet());
    }

    @Test
    void notify_uses_connection_bundle_scope_from_request_not_config() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("acme|u1"); req.setMessage("Approve?");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email phone", "https://idp.acme.example/.well-known/openid-configuration"));
        provider.notify(req).blockingGet();
        ArgumentCaptor<CibaHints> hints = ArgumentCaptor.forClass(CibaHints.class);
        verify(acmeAuth).bcAuthorize(hints.capture(), eq("openid profile email phone"), eq("Approve?"), any());
        assertEquals("acme|u1", hints.getValue().loginHint());
        assertNull(hints.getValue().loginHintToken());
    }

    /** capabilities() must return exactly {AUTHORIZATION_DETAILS} — post-E3, federation dependency is
     *  signaled via IdentityProviderDependent rather than a capability flag. */
    @Test
    void capabilities_returns_fixed_intrinsic_set() {
        Set<NotifierCapability> caps = provider.capabilities();
        assertEquals(Set.of(NotifierCapability.AUTHORIZATION_DETAILS), caps,
                "must expose exactly {AUTHORIZATION_DETAILS}");
    }

    @Test
    public void is_identity_provider_dependent_and_exposes_configured_idp() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg); // package-visible test seam

        assertTrue(p instanceof io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent);
        assertEquals(java.util.Optional.of("idp-acme"),
                ((io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent) p).getIdentityProviderId());
    }

    @Test
    public void capabilities_no_longer_advertise_federated_hint_resolution() {
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        assertEquals(java.util.Set.of(io.gravitee.am.authdevice.notifier.api.model.NotifierCapability.AUTHORIZATION_DETAILS),
                p.capabilities());
    }

    @Test
    public void config_binding_fails_closed_when_identity_provider_id_absent() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId(null);
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }

    @Test
    public void config_binding_fails_closed_when_identity_provider_id_blank() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("   ");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }

    @Test
    public void config_binding_succeeds_for_valid_config() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertDoesNotThrow(p::afterPropertiesSet);
    }

    /** notify() must obtain the downstream CIBA client from the injected CibaClientFactory, passing the
     *  per-request FederatedConnection and the notifier's configured resourceAudience — no test-vs-prod branch. */
    @Test
    void notify_builds_client_via_factory_from_connection_and_configured_audience() {
        CibaClientFactory factory = mock(CibaClientFactory.class);
        when(factory.create(any(), any(), any())).thenReturn(acmeAuth);
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                factory, stubResolver(), new TestStrategy(), /*hintStrategy*/ null);
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setResourceAudience("https://api.example");
        p.setConfiguration(cfg);

        var conn = new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration");
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidF"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid"));
        req.setConnection(conn);

        p.notify(req).blockingGet();

        verify(factory).create(eq(conn), eq("https://api.example"), any());
    }

    @Test void selected_hint_strategy_decorates_using_discovery_issuer() {
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), null, new WrappingHintStrategy());
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidH"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));
        p.notify(req).blockingGet();
        ArgumentCaptor<CibaHints> hints = ArgumentCaptor.forClass(CibaHints.class);
        verify(acmeAuth).bcAuthorize(hints.capture(), any(), any(), any());
        assertEquals("wrapped(https://idp.acme.example/|acme|u1)", hints.getValue().loginHint());
    }

    @Test void no_hint_fails_closed() {
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), null, null);
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidN"); req.setState("s"); // no hint set
        req.setScopes(Set.of("openid"));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));
        assertThrows(IllegalStateException.class, () -> p.notify(req).blockingGet());
    }

    @Test
    void notify_without_rar_records_only_the_upstream_handle() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid"));
        req.setConnection(CONNECTION);

        var resp = provider.notify(req).blockingGet();

        assertEquals(Map.of(CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1"), resp.getExtraData());
    }

    @Test
    void extractUserResponse_ignores_callbacks() {
        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .set("tid", "tid1").set("state", "stateJwt").set("validated", "true").set("id_token", "eyJ.id.tok");
        ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), params);

        assertFalse(provider.extractUserResponse(ctx).blockingGet().isPresent());
    }

    @Test
    void checkStatus_pending_upstream_has_no_decision() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.PENDING, null, null, null, "authorization_pending")));

        Optional<ADUserResponse> decision = provider.checkStatus(statusRequest(Map.of(
                CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1"))).blockingGet();

        assertTrue(decision.isEmpty());
    }

    @Test
    void checkStatus_token_approves_with_federated_identity() {
        var rar = List.of(Map.<String, Object>of("type", "x"));
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        provider.setConfiguration(cfg);
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", "id.tok", rar, null)));

        ADUserResponse decision = provider.checkStatus(statusRequest(Map.of(
                CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1",
                CibaFederationAuthenticationDeviceNotifierProvider.RELAYED_AD_HASH, CrossWitness.hash(rar)))).blockingGet().orElseThrow();

        assertTrue(decision.isValidated());
        assertEquals("tid1", decision.getTid());
        assertEquals("id.tok", decision.getIdToken());
        assertEquals("AT", decision.getAccessToken());
        assertEquals("idp-acme", decision.getIdentityProviderId());
    }

    @Test
    void checkStatus_builds_client_from_connection_and_configured_audience() {
        CibaClientFactory factory = mock(CibaClientFactory.class);
        when(factory.create(any(), any(), any())).thenReturn(acmeAuth);
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.PENDING, null, null, null, "authorization_pending")));
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(factory, stubResolver(), null, null);
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setResourceAudience("https://api.example");
        p.setConfiguration(cfg);

        p.checkStatus(statusRequest(Map.of(CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1"))).blockingGet();

        verify(factory).create(eq(CONNECTION), eq("https://api.example"), any());
    }

    @Test
    void checkStatus_upstream_failure_has_no_decision_so_the_client_polls_again() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.error(new IllegalStateException("connection reset")));

        Optional<ADUserResponse> decision = provider.checkStatus(statusRequest(Map.of(
                CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1"))).blockingGet();

        assertTrue(decision.isEmpty());
    }

    @Test
    void checkStatus_synchronous_failure_has_no_decision() {
        OidcDiscoveryResolver throwing = mock(OidcDiscoveryResolver.class);
        when(throwing.resolve(any())).thenThrow(new IllegalStateException("resolver not ready"));
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest((conn, aud, meta) -> acmeAuth, throwing, null, null);
        Map<String, Object> state = Map.of(CibaFederationAuthenticationDeviceNotifierProvider.UPSTREAM_AUTH_REQ_ID, "R1");

        assertTrue(p.checkStatus(statusRequest(state)).blockingGet().isEmpty());
        assertTrue(provider.checkStatus(new ADStatusRequest("tid1", state, null)).blockingGet().isEmpty());
    }

    @Test
    void checkStatus_without_upstream_handle_fails_closed() {
        Optional<ADUserResponse> decision = provider.checkStatus(statusRequest(Map.of())).blockingGet();

        assertFalse(decision.orElseThrow().isValidated());
        verifyNoInteractions(acmeAuth);
    }
}
