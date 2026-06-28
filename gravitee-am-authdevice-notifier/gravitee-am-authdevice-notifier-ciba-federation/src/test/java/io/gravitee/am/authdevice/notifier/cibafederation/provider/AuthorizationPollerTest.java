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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationPollerTest {

    CibaClient acmeAuth; GatewayCallbackClient callback; PendingAuthStore store; AuthorizationPoller poller;
    final String CB_URL = "http://gw/ciba/authenticate/callback";

    @BeforeEach void init() {
        acmeAuth = mock(CibaClient.class);
        callback = mock(GatewayCallbackClient.class);
        store = new PendingAuthStore();
        store.put(new PendingAuthStore.Pending("tid1", "stateJwt", "R1", 5, 9_999_999_999L, hashOfSent(), CB_URL));
        when(callback.postCallback(anyString(), anyString(), anyString(), anyBoolean(), nullable(String.class), nullable(String.class))).thenReturn(Completable.complete());
        poller = new AuthorizationPoller(callback, store, () -> 1000L);
        poller.seedClient("tid1", acmeAuth);
    }

    private String hashOfSent() { return CrossWitness.hash(List.of(Map.of("type", "x"))); }

    @Test
    void pending_keeps_record() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(CibaClient.PollKind.PENDING, null, null, null, "authorization_pending")));
        assertEquals(AuthorizationPoller.Outcome.CONTINUE, poller.pollOnce("tid1").blockingGet());
        assertNotNull(store.get("tid1"));
        verifyNoInteractions(callback);
    }

    @Test
    void token_fires_validated_true_when_witness_matches() {
        // adHashPreSend (from @BeforeEach) hashes [{"type":"x"}]; the OP echoes the same details AND an
        // id_token -> the cross-witness matches and the transaction is approved.
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", "id.tok", List.of(Map.of("type", "x")), null)));
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(true), eq("id.tok"), eq("AT"));
        assertNull(store.get("tid1"));
        assertFalse(poller.hasClient("tid1"), "clientsByTid must be cleaned on APPROVED");
    }

    @Test
    void token_witness_mismatch_fails_closed() {
        // RAR was sent (adHashPreSend != null) but the OP echoed DIFFERENT authorization_details
        // (consent substitution) -> fail closed with a validated=false callback.
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", "id.tok", List.of(Map.of("type", "TAMPERED")), null)));
        assertEquals(AuthorizationPoller.Outcome.DENIED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(false), nullable(String.class), nullable(String.class));
        assertNull(store.get("tid1"));
        assertFalse(poller.hasClient("tid1"), "clientsByTid must be cleaned on DENIED");
    }

    @Test
    void token_without_rar_skips_witness_and_approves() {
        // No RAR was relayed (adHashPreSend == null): the cross-witness does not apply and must not
        // block a non-RAR flow, as long as an id_token is present.
        store.put(new PendingAuthStore.Pending("tid1", "stateJwt", "R1", 5, 9_999_999_999L, null, CB_URL));
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", "id.tok", null, null)));
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(true), eq("id.tok"), eq("AT"));
    }

    @Test
    void token_without_id_token_fails_closed() {
        // Witness matches, but a federation TOKEN carrying no id_token cannot assert identity -> fail closed.
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", null, List.of(Map.of("type", "x")), null)));
        assertEquals(AuthorizationPoller.Outcome.DENIED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(false), nullable(String.class), nullable(String.class));
        assertNull(store.get("tid1"));
    }

    @Test
    void slow_down_yields_slow_down_outcome_and_keeps_record() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.SLOW_DOWN, null, null, null, "slow_down")));
        assertEquals(AuthorizationPoller.Outcome.SLOW_DOWN, poller.pollOnce("tid1").blockingGet());
        assertNotNull(store.get("tid1"));
        verifyNoInteractions(callback);
    }

    @Test
    void no_client_for_known_tid_fires_validated_false_callback() {
        // store holds the pending entry but no per-tid client is present -> the relying client is still
        // owed a terminal answer, so we fail closed with a callback (not a silent discard).
        AuthorizationPoller orphan = new AuthorizationPoller(callback, store, () -> 1000L);
        assertEquals(AuthorizationPoller.Outcome.GONE, orphan.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(false), nullable(String.class), nullable(String.class));
        assertNull(store.get("tid1"));
    }

    @Test
    void token_forwards_id_token_to_callback() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "at", "eyJ.id.tok", List.of(Map.of("type", "x")), null)));
        assertEquals(AuthorizationPoller.Outcome.APPROVED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(true), eq("eyJ.id.tok"), eq("at"));
        assertNull(store.get("tid1"));
        assertFalse(poller.hasClient("tid1"), "clientsByTid must be cleaned on APPROVED");
    }

    @Test
    void error_fires_validated_false_and_clears() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(CibaClient.PollKind.ERROR, null, null, null, "access_denied")));
        assertEquals(AuthorizationPoller.Outcome.DENIED, poller.pollOnce("tid1").blockingGet());
        verify(callback).postCallback(eq(CB_URL), eq("stateJwt"), eq("tid1"), eq(false), nullable(String.class), nullable(String.class));
        assertNull(store.get("tid1"));
        assertFalse(poller.hasClient("tid1"), "clientsByTid must be cleaned on DENIED");
    }

    @Test
    void expired_clears_without_callback() {
        store.put(new PendingAuthStore.Pending("tid1", "stateJwt", "R1", 5, 1L, hashOfSent(), CB_URL));
        poller = new AuthorizationPoller(callback, store, () -> 9_999_999_999_999L);
        poller.seedClient("tid1", acmeAuth);
        assertEquals(AuthorizationPoller.Outcome.EXPIRED, poller.pollOnce("tid1").blockingGet());
        verifyNoInteractions(acmeAuth);
        verifyNoInteractions(callback);
        assertNull(store.get("tid1"));
        assertFalse(poller.hasClient("tid1"), "clientsByTid must be cleaned on EXPIRED");
    }

    @Test
    void gone_when_tid_not_in_store() {
        assertEquals(AuthorizationPoller.Outcome.GONE, poller.pollOnce("unknown").blockingGet());
        verifyNoInteractions(acmeAuth);
        verifyNoInteractions(callback);
    }

    @Test
    void token_callback_failure_propagates_error_and_clears_store() {
        when(acmeAuth.pollToken("R1")).thenReturn(Single.just(new CibaClient.PollResult(
                CibaClient.PollKind.TOKEN, "AT", "id.tok", java.util.List.of(java.util.Map.of("type", "x")), null)));
        when(callback.postCallback(anyString(), anyString(), anyString(), anyBoolean(), nullable(String.class), nullable(String.class)))
                .thenReturn(Completable.error(new IllegalStateException("callback rejected: 503")));
        assertThrows(Exception.class, () -> poller.pollOnce("tid1").blockingGet());
        assertNull(store.get("tid1")); // doFinally ran despite callback failure
    }

    @Test
    void schedule_error_in_poll_cleans_client_and_store() throws Exception {
        // Arrange: pollToken emits an error so the schedule() error handler fires.
        // We observe completion by spying on PendingAuthStore.remove() via a subclass latch.
        CountDownLatch latch = new CountDownLatch(1);
        PendingAuthStore latchStore = new PendingAuthStore() {
            @Override public void remove(String tid) { super.remove(tid); latch.countDown(); }
        };
        latchStore.put(new PendingAuthStore.Pending("tid1", "stateJwt", "R1", 5, 9_999_999_999L, hashOfSent(), CB_URL));
        AuthorizationPoller latchPoller = new AuthorizationPoller(callback, latchStore, () -> 1000L);
        when(acmeAuth.pollToken("R1")).thenReturn(Single.error(new RuntimeException("upstream failure")));
        Vertx vertx = Vertx.vertx();
        try {
            latchPoller.schedule(vertx, "tid1", 0 /* effectively 1 ms */, acmeAuth);
            // Wait for the error handler (which calls store.remove) to fire (up to 5s)
            assertTrue(latch.await(5, TimeUnit.SECONDS), "error handler did not fire within timeout");
            assertNull(latchStore.get("tid1"), "store must be cleaned on poll error");
            assertFalse(latchPoller.hasClient("tid1"), "clientsByTid must be cleaned on poll error");
        } finally {
            vertx.close().blockingAwait();
        }
    }
}
