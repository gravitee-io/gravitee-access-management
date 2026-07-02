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

    CibaClient auth0; ConsentRelayStrategy consentStrategy; PendingAuthStore store; AuthorizationPoller poller;
    CibaFederationAuthenticationDeviceNotifierProvider provider;

    @BeforeEach void init() {
        auth0 = mock(CibaClient.class);
        consentStrategy = new Auth0UserProfileRelayStrategy("Acme");
        store = new PendingAuthStore();
        poller = mock(AuthorizationPoller.class);
        when(auth0.bcAuthorize(any(), any(), any(), any(), any()))
                .thenReturn(Single.just(new CibaClient.BcAuthorizeResult("R1", 120, 5)));
        provider = CibaFederationAuthenticationDeviceNotifierProvider.forTest(
                auth0, consentStrategy, store, poller, /*vertx*/ null, /*maxLifetimeSeconds*/ 120);
    }

    @Test
    void notify_relays_hint_verbatim_and_transformed_consent() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("auth0|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setCallbackUrl("http://gw/ciba/callback");
        req.setAuthorizationDetails(List.of(Map.of("type", "fdx_v1.0",
                "consentRequest", Map.of("durationType", "ONE_TIME",
                        "resources", List.of(Map.of("dataClusters", List.of("ACCOUNT_BASIC")))))));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email", "https://dev.auth0.com/.well-known/openid-configuration"));

        var resp = provider.notify(req).blockingGet();

        assertEquals("tid1", resp.getTransactionId());
        ArgumentCaptor<Object> rar = ArgumentCaptor.forClass(Object.class);
        verify(auth0).bcAuthorize(eq("auth0|u1"), isNull(), eq("openid profile email"), eq("Approve?"), rar.capture());
        assertTrue(CrossWitness.canonical(rar.getValue()).contains("user-profile"));
        PendingAuthStore.Pending p = store.get("tid1");
        assertNotNull(p);
        assertEquals("R1", p.authReqId());
        assertNotNull(p.adHashPreSend());
        assertEquals("http://gw/ciba/callback", p.callbackUrl());
        verify(poller).schedule(any(), eq("tid1"), eq(5), any());
    }

    @Test
    void notify_propagates_bcauthorize_error_and_stores_nothing() {
        when(auth0.bcAuthorize(any(), any(), any(), any(), any()))
                .thenReturn(io.reactivex.rxjava3.core.Single.error(new IllegalStateException("bc-authorize failed: invalid_client")));
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidX"); req.setState("s"); req.setLoginHint("auth0|u1");
        req.setScopes(java.util.Set.of("openid"));
        req.setCallbackUrl("http://gw/ciba/callback");
        req.setAuthorizationDetails(java.util.List.of(java.util.Map.of("type", "fdx_v1.0",
                "consentRequest", java.util.Map.of("durationType", "ONE_TIME",
                        "resources", java.util.List.of(java.util.Map.of("dataClusters", java.util.List.of("ACCOUNT_BASIC")))))));
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email", "https://dev.auth0.com/.well-known/openid-configuration"));

        assertThrows(Exception.class, () -> provider.notify(req).blockingGet());
        assertNull(store.get("tidX"));
        verify(poller, never()).schedule(any(), anyString(), anyInt(), any());
    }

    @Test
    void notify_uses_connection_bundle_scope_from_request_not_config() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tid1"); req.setState("stateJwt"); req.setLoginHint("auth0|u1"); req.setMessage("Approve?");
        req.setCallbackUrl("http://gw/ciba/callback");
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid profile email phone", "https://dev.auth0.com/.well-known/openid-configuration"));
        provider.notify(req).blockingGet();
        verify(auth0).bcAuthorize(eq("auth0|u1"), isNull(), eq("openid profile email phone"), eq("Approve?"), any());
    }

    @Test
    void extractUserResponse_returns_present_with_all_fields() {
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
    }

    @Test
    void extractUserResponse_returns_empty_when_required_params_missing() {
        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .set(CibaFederationAuthenticationDeviceNotifierProvider.STATE, "stateJwt");
        ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), params);

        Optional<ADUserResponse> result = provider.extractUserResponse(ctx).blockingGet();

        assertFalse(result.isPresent());
    }

    // --- New tests: Tasks 11+12 ---

    /** capabilities() must return exactly {FEDERATED_HINT_RESOLUTION, AUTHORIZATION_DETAILS} — intrinsic by type (§2.2). */
    @Test
    void capabilities_returns_fixed_intrinsic_set() {
        Set<NotifierCapability> caps = provider.capabilities();
        assertEquals(2, caps.size(), "must expose exactly 2 capabilities");
        assertTrue(caps.contains(NotifierCapability.FEDERATED_HINT_RESOLUTION),
                "must include FEDERATED_HINT_RESOLUTION");
        assertTrue(caps.contains(NotifierCapability.AUTHORIZATION_DETAILS),
                "must include AUTHORIZATION_DETAILS");
    }

    /** notify() threads request.getCallbackUrl() into PendingAuthStore.Pending so the poller uses the per-tid URL. */
    @Test
    void notify_threads_callbackUrl_from_request_into_pending_store() {
        String expectedCbUrl = "http://gw/ciba/my-domain/callback";
        ADNotificationRequest req = new ADNotificationRequest();
        req.setTransactionId("tidCb"); req.setState("s"); req.setLoginHint("auth0|u1");
        req.setScopes(Set.of("openid")); req.setMessage("Approve?");
        req.setCallbackUrl(expectedCbUrl);
        req.setConnection(new io.gravitee.am.authdevice.notifier.api.model.FederatedConnection(
                "cid", "secret", "openid", "https://dev.auth0.com/.well-known/openid-configuration"));

        provider.notify(req).blockingGet();

        PendingAuthStore.Pending p = store.get("tidCb");
        assertNotNull(p, "pending entry must be present after notify");
        assertEquals(expectedCbUrl, p.callbackUrl(),
                "callbackUrl in PendingAuthStore.Pending must match request.getCallbackUrl()");
    }
}
