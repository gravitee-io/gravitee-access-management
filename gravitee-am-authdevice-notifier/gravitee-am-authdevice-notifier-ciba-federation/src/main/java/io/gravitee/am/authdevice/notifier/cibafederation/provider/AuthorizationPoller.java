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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import lombok.CustomLog;

@CustomLog
public class AuthorizationPoller {


    /** RFC 8628 §3.5 / CIBA: on slow_down the client must lengthen the poll interval by 5 seconds. */
    private static final long SLOW_DOWN_BACKOFF_MS = 5_000L;

    public enum Outcome { CONTINUE, SLOW_DOWN, APPROVED, DENIED, EXPIRED, GONE }

    private final Map<String, CibaClient> clientsByTid = new ConcurrentHashMap<>();
    private final GatewayCallbackClient callback;
    private final PendingAuthStore store;
    private final LongSupplier nowEpochSeconds;

    public AuthorizationPoller(GatewayCallbackClient callback, PendingAuthStore store,
                               LongSupplier nowEpochSeconds) {
        this.callback = callback; this.store = store;
        this.nowEpochSeconds = nowEpochSeconds;
    }

    /** Package-private: seed a per-tid client (used by tests for pollOnce-only scenarios). */
    void seedClient(String tid, CibaClient client) {
        clientsByTid.put(tid, client);
    }

    /** Package-private: check whether a per-tid client entry is still present (used by tests to verify cleanup). */
    boolean hasClient(String tid) {
        return clientsByTid.containsKey(tid);
    }

    public Single<Outcome> pollOnce(String tid) {
        PendingAuthStore.Pending p = store.get(tid);
        if (p == null) return Single.just(Outcome.GONE);
        if (p.isExpired(nowEpochSeconds.getAsLong())) {
            cleanup(tid);
            return Single.just(Outcome.EXPIRED);
        }
        CibaClient cibaClient = clientsByTid.get(tid);
        if (cibaClient == null) {
            // We still hold the pending entry, so the relying client is owed a terminal answer.
            log.error("CIBA-FED no client for tid={}; failing the transaction closed", tid);
            return failClosed(p, tid).andThen(Single.just(Outcome.GONE));
        }
        // If the gateway callback POST fails, postCallback emits a Completable error which propagates
        // to schedule()'s onError: the loop stops and the pending entry is cleaned (doFinally). There is
        // no callback retry — the relying client re-initiates.
        final String callbackUrl = p.callbackUrl();
        return cibaClient.pollToken(p.authReqId()).flatMap(res -> switch (res.kind()) {
            case PENDING -> Single.just(Outcome.CONTINUE);
            case SLOW_DOWN -> Single.just(Outcome.SLOW_DOWN);
            case TOKEN -> {
                // Cross-witness applies only when we relayed authorization_details: matchesHash is
                // vacuously false when nothing was sent (adHashPreSend == null), so guard on rar_sent
                // to avoid failing every non-RAR flow. When RAR WAS sent, a mismatch means the OP
                // approved different/absent details than the client requested (consent substitution)
                // and we fail closed.
                final boolean rarSent = p.adHashPreSend() != null;
                final boolean witnessOk = !rarSent || CrossWitness.matchesHash(p.adHashPreSend(), res.authorizationDetails());
                final String idToken = res.idToken();
                log.info("CIBA-FED witness tid={} rar_sent={} cross_witness_match={} id_token_present={}",
                        tid, rarSent, witnessOk, idToken != null);
                if (!witnessOk) {
                    log.error("CIBA-FED consent cross-witness FAILED tid={}: OP authorization_details differ from what was relayed; failing closed", tid);
                    yield failClosed(p, tid).andThen(Single.just(Outcome.DENIED));
                }
                if (idToken == null) {
                    // A federation notifier's contract is a federated identity assertion; a TOKEN with
                    // no id_token cannot establish identity, independent of any remote userinfo toggle.
                    log.error("CIBA-FED TOKEN response without id_token tid={}; failing closed", tid);
                    yield failClosed(p, tid).andThen(Single.just(Outcome.DENIED));
                }
                yield callback.postCallback(callbackUrl, p.state(), tid, true, idToken, res.accessToken())
                        .doFinally(() -> cleanup(tid))
                        .andThen(Single.just(Outcome.APPROVED));
            }
            case ERROR -> failClosed(p, tid).andThen(Single.just(Outcome.DENIED));
        });
    }

    /** Best-effort terminal "rejected" callback so the relying client is never left polling to its own expiry. */
    private Completable failClosed(PendingAuthStore.Pending p, String tid) {
        return callback.postCallback(p.callbackUrl(), p.state(), tid, false, null, null)
                .doFinally(() -> cleanup(tid));
    }

    private void cleanup(String tid) {
        store.remove(tid);
        clientsByTid.remove(tid);
    }

    /** Schedule recurring polls until a terminal outcome; one-shot re-arm avoids overlapping polls. */
    public void schedule(Vertx vertx, String tid, int intervalSeconds, CibaClient client) {
        clientsByTid.put(tid, client);
        long periodMs = Math.max(1, intervalSeconds) * 1000L;
        pollAndReschedule(vertx, tid, periodMs);
    }

    private void pollAndReschedule(Vertx vertx, String tid, long periodMs) {
        vertx.setTimer(periodMs, timerId ->
                pollOnce(tid).subscribe(
                        outcome -> {
                            switch (outcome) {
                                case CONTINUE -> pollAndReschedule(vertx, tid, periodMs); // re-arm only after this poll finished
                                case SLOW_DOWN -> pollAndReschedule(vertx, tid, periodMs + SLOW_DOWN_BACKOFF_MS); // lengthen per RFC 8628
                                default -> { /* terminal (APPROVED/DENIED/EXPIRED/GONE): stop, do not re-arm */ }
                            }
                        },
                        err -> {
                            // Terminal upstream/discovery failure. Tell the relying client (best-effort) so it
                            // isn't left seeing authorization_pending until its own expiry, then stop.
                            log.error("CIBA-FED poll error tid={}: {}", tid, err.getMessage(), err);
                            PendingAuthStore.Pending p = store.get(tid);
                            if (p != null) {
                                callback.postCallback(p.callbackUrl(), p.state(), tid, false, null, null)
                                        .subscribe(() -> {}, e -> log.warn("CIBA-FED terminal callback failed tid={}: {}", tid, e.getMessage()));
                            }
                            cleanup(tid);
                        }));
    }
}
