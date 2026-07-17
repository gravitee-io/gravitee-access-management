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
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
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

    CibaClient acmeAuth; ConsentRelayStrategy consentStrategy; PendingAuthStore store; AuthorizationPoller poller;
    CibaFederationAuthenticationDeviceNotifierProvider provider;

    @BeforeEach void init() {
        acmeAuth = mock(CibaClient.class);
        consentStrategy = null; // blank config → raw relay (no transform)
        store = new PendingAuthStore();
        poller = mock(AuthorizationPoller.class);
        when(acmeAuth.bcAuthorize(any(), any(), any(), any()))
                .thenReturn(Single.just(new CibaClient.BcAuthorizeResult("R1", 120, 5)));
        provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), consentStrategy, /*hintStrategy*/ null,
                store, poller, /*vertx*/ null, /*maxLifetimeSeconds*/ 120);
    }

    @Test
    void notify_relays_hint_verbatim_and_consent_via_configured_strategy() {
        // Proves notify() threads a configured (non-blank) strategy through consentStrategy.relay(rar, ctx)
        // end-to-end (hint verbatim, consent relayed, store + poller wired). The raw-vs-applied seam itself
        // (blank -> unchanged, selected -> transformed) is covered by the two seam tests below, so this
        // stays a stateless identity double rather than a vendor transform.
        provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), new TestStrategy(), /*hintStrategy*/ null,
                store, poller, /*vertx*/ null, /*maxLifetimeSeconds*/ 120);
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setCallbackUrl("http://gw/ciba/callback");
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
        PendingAuthStore.Pending p = store.get("tid1");
        assertNotNull(p);
        assertEquals("R1", p.authReqId());
        assertNotNull(p.adHashPreSend());
        assertEquals("http://gw/ciba/callback", p.callbackUrl());
        verify(poller).schedule(any(), eq("tid1"), eq(5), any());
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
        req.setCallbackUrl("http://gw/ciba/callback");
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
                (conn, aud, meta) -> acmeAuth, stubResolver(), new TaggingTestStrategy(), /*hintStrategy*/ null,
                store, poller, /*vertx*/ null, /*maxLifetimeSeconds*/ 120);
        var inputRar = List.of(Map.<String, Object>of("type", "fdx_v1.0",
                "consentRequest", Map.of("durationType", "ONE_TIME",
                        "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC"))))));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidApplied"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setCallbackUrl("http://gw/ciba/callback");
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
    void notify_propagates_bcauthorize_error_and_stores_nothing() {
        when(acmeAuth.bcAuthorize(any(), any(), any(), any()))
                .thenReturn(io.reactivex.rxjava3.core.Single.error(new IllegalStateException("bc-authorize failed: invalid_client")));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidX"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(java.util.Set.of("openid"));
        req.setCallbackUrl("http://gw/ciba/callback");
        req.setAuthorizationDetails(java.util.List.of(java.util.Map.of("type", "fdx_v1.0",
                "consentRequest", java.util.Map.of("durationType", "ONE_TIME",
                        "resources", java.util.List.of(java.util.Map.of("dataClusters", java.util.List.of("ACCOUNT_BASIC")))))));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email", "https://idp.acme.example/.well-known/openid-configuration"));

        assertThrows(Exception.class, () -> provider.notify(req).blockingGet());
        assertNull(store.get("tidX"));
        verify(poller, never()).schedule(any(), anyString(), anyInt(), any());
    }

    @Test
    void notify_uses_connection_bundle_scope_from_request_not_config() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("acme|u1"); req.setMessage("Approve?");
        req.setCallbackUrl("http://gw/ciba/callback");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email phone", "https://idp.acme.example/.well-known/openid-configuration"));
        provider.notify(req).blockingGet();
        ArgumentCaptor<CibaHints> hints = ArgumentCaptor.forClass(CibaHints.class);
        verify(acmeAuth).bcAuthorize(hints.capture(), eq("openid profile email phone"), eq("Approve?"), any());
        assertEquals("acme|u1", hints.getValue().loginHint());
        assertNull(hints.getValue().loginHintToken());
    }

    @Test
    void extractUserResponse_returns_present_with_all_fields() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        provider.setConfiguration(cfg);

        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .set(CibaFederationAuthenticationDeviceNotifierProvider.TRANSACTION_ID, "tid1")
                .set(CibaFederationAuthenticationDeviceNotifierProvider.STATE, "stateJwt")
                .set(CibaFederationAuthenticationDeviceNotifierProvider.CALLBACK_VALIDATE, "true")
                .set(CibaFederationAuthenticationDeviceNotifierProvider.ID_TOKEN, "eyJ.id.tok")
                .set(CibaFederationAuthenticationDeviceNotifierProvider.ACCESS_TOKEN, "eyJ.at.tok");
        ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), params);

        Optional<ADUserResponse> result = provider.extractUserResponse(ctx).blockingGet();

        assertTrue(result.isPresent());
        ADUserResponse resp = result.get();
        assertEquals("tid1", resp.getTid());
        assertEquals("stateJwt", resp.getState());
        assertTrue(resp.isValidated());
        assertEquals("eyJ.id.tok", resp.getIdToken());
        assertEquals("eyJ.at.tok", resp.getAccessToken());
        assertEquals("idp-acme", resp.getIdentityProviderId(),
                "extractUserResponse must stamp the notifier's configured identityProviderId onto the response");
    }

    @Test
    void extractUserResponse_returns_empty_when_required_params_missing() {
        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .set(CibaFederationAuthenticationDeviceNotifierProvider.STATE, "stateJwt");
        ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), params);

        Optional<ADUserResponse> result = provider.extractUserResponse(ctx).blockingGet();

        assertFalse(result.isPresent());
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
    public void config_binding_fails_closed_when_callback_client_id_absent() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setCallbackClientId(null);
        cfg.setCallbackClientSecret("s");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }

    @Test
    public void config_binding_fails_closed_when_callback_client_secret_absent() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setCallbackClientId("cbid");
        cfg.setCallbackClientSecret("  ");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }

    @Test
    public void config_binding_fails_closed_when_callback_auth_method_unsupported() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setCallbackClientId("cbid");
        cfg.setCallbackClientSecret("cbsec");
        cfg.setCallbackClientAuthMethod("private_key_jwt");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }

    @Test
    public void config_binding_succeeds_for_valid_config() {
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setCallbackClientId("cbid");
        cfg.setCallbackClientSecret("cbsec");
        cfg.setCallbackClientAuthMethod("client_secret_basic");
        CibaFederationAuthenticationDeviceNotifierProvider p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.setConfiguration(cfg);
        assertDoesNotThrow(p::afterPropertiesSet);
    }

    /** notify() threads request.getCallbackUrl() into PendingAuthStore.Pending so the poller uses the per-tid URL. */
    @Test
    void notify_threads_callbackUrl_from_request_into_pending_store() {
        String expectedCbUrl = "http://gw/ciba/my-domain/callback";
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidCb"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setCallbackUrl(expectedCbUrl);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));

        provider.notify(req).blockingGet();

        PendingAuthStore.Pending p = store.get("tidCb");
        assertNotNull(p, "pending entry must be present after notify");
        assertEquals(expectedCbUrl, p.callbackUrl(),
                "callbackUrl in PendingAuthStore.Pending must match request.getCallbackUrl()");
    }

    /** notify() must obtain the downstream CIBA client from the injected CibaClientFactory, passing the
     *  per-request FederatedConnection and the notifier's configured resourceAudience — no test-vs-prod branch. */
    @Test
    void notify_builds_client_via_factory_from_connection_and_configured_audience() {
        CibaClientFactory factory = mock(CibaClientFactory.class);
        when(factory.create(any(), any(), any())).thenReturn(acmeAuth);
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                factory, stubResolver(), new TestStrategy(), /*hintStrategy*/ null,
                new PendingAuthStore(), mock(AuthorizationPoller.class), null, 120);
        CibaFederationAuthenticationDeviceNotifierConfiguration cfg = new CibaFederationAuthenticationDeviceNotifierConfiguration();
        cfg.setIdentityProviderId("idp-acme");
        cfg.setResourceAudience("https://api.example");
        p.setConfiguration(cfg);

        var conn = new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration");
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidF"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setCallbackUrl("http://gw/ciba/callback");
        req.setConnection(conn);

        p.notify(req).blockingGet();

        verify(factory).create(eq(conn), eq("https://api.example"), any());
    }

    @Test void selected_hint_strategy_decorates_using_discovery_issuer() {
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), null, new WrappingHintStrategy(),
                store, poller, null, 120);
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidH"); req.setState("s"); req.setLoginHint("acme|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?"); req.setCallbackUrl("http://gw/cb");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));
        p.notify(req).blockingGet();
        ArgumentCaptor<CibaHints> hints = ArgumentCaptor.forClass(CibaHints.class);
        verify(acmeAuth).bcAuthorize(hints.capture(), any(), any(), any());
        assertEquals("wrapped(https://idp.acme.example/|acme|u1)", hints.getValue().loginHint());
    }

    @Test void no_hint_fails_closed() {
        var p = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                (conn, aud, meta) -> acmeAuth, stubResolver(), null, null, store, poller, null, 120);
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidN"); req.setState("s"); // no hint set
        req.setScopes(Set.of("openid")); req.setCallbackUrl("http://gw/cb");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://idp.acme.example/.well-known/openid-configuration"));
        assertThrows(IllegalStateException.class, () -> p.notify(req).blockingGet());
    }
}
