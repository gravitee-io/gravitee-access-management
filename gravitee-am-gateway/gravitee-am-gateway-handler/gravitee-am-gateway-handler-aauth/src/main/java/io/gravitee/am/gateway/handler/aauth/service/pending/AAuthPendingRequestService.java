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
package io.gravitee.am.gateway.handler.aauth.service.pending;

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.repository.oidc.api.AAuthPendingRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing AAUTH pending requests (deferred authorization flow).
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthPendingRequestService {

    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_CLARIFICATION_ROUNDS = 5; // per spec Section 7.3.4
    private static final int RETENTION_BUFFER_SECONDS = 900; // 15 min retention after TTL

    private final AAuthPendingRequestRepository repository;

    /**
     * Create a new pending request for the deferred authorization flow.
     */
    public Single<AAuthPendingRequest> create(String domain, String agentServerUrl, String agentIdentifier,
                                                String agentJkt, PublicKey agentPublicKey,
                                                String applicationId, String resourceIss,
                                                String scope, String justification,
                                                String loginHint, String domainHint, String tenant,
                                                boolean clarificationSupported,
                                                String psIssuerUrl, int ttlSeconds) {
        AAuthPendingRequest request = new AAuthPendingRequest();
        request.setId(UUID.randomUUID().toString());
        request.setStatus(PendingRequestStatus.PENDING.name());
        request.setDomain(domain);
        request.setAgentServerUrl(agentServerUrl);
        request.setAgentIdentifier(agentIdentifier);
        request.setAgentJkt(agentJkt);
        request.setAgentPublicKey(serializePublicKey(agentPublicKey));
        request.setApplicationId(applicationId);
        request.setResourceIss(resourceIss);
        request.setScope(scope);
        request.setJustification(justification);
        request.setLoginHint(loginHint);
        request.setDomainHint(domainHint);
        request.setTenant(tenant);
        request.setInteractionCode(generateInteractionCode());
        request.setClarificationSupported(clarificationSupported);
        request.setClarificationRoundCount(0);
        request.setPsIssuerUrl(psIssuerUrl);

        Date now = new Date();
        request.setCreatedAt(now);
        request.setLastAccessAt(now);
        request.setExpireAt(new Date(now.getTime() + (ttlSeconds + RETENTION_BUFFER_SECONDS) * 1000L));

        return repository.create(request);
    }

    /**
     * Retrieve a pending request for agent polling. Enforces agent key matching
     * and minimum poll interval.
     *
     * @return the pending request, or empty if not found/expired
     */
    public Maybe<AAuthPendingRequest> poll(String id, String agentJkt) {
        return repository.findById(id)
                .flatMap(request -> {
                    if (!Objects.equals(request.getAgentJkt(), agentJkt)) {
                        return Maybe.error(new SecurityException("Agent key mismatch"));
                    }

                    long elapsed = System.currentTimeMillis() - request.getLastAccessAt().getTime();
                    if (elapsed < DEFAULT_POLL_INTERVAL_SECONDS * 1000L) {
                        return Maybe.error(new TooFastException());
                    }

                    // Consume-once: if COMPLETED, delete the record so next poll returns 410
                    if (PendingRequestStatus.COMPLETED.name().equals(request.getStatus())) {
                        return repository.delete(id).andThen(Maybe.just(request));
                    }

                    request.setLastAccessAt(new Date());
                    return repository.update(request).toMaybe();
                });
    }

    /**
     * Find a pending request by interaction code (used by the interaction endpoint).
     */
    public Maybe<AAuthPendingRequest> findByInteractionCode(String code) {
        return repository.findByInteractionCode(code);
    }

    /**
     * Mark a pending request as INTERACTING (user has arrived at the interaction endpoint).
     */
    public Completable markInteracting(String id, String userId) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(() -> new PendingRequestNotFoundException(id)))
                .flatMap(request -> {
                    request.setStatus(PendingRequestStatus.INTERACTING.name());
                    request.setUserId(userId);
                    return repository.update(request);
                })
                .ignoreElement();
    }

    /**
     * Mark a pending request as completed with the auth token.
     */
    public Single<AAuthPendingRequest> approve(String id, String authToken, long expiresIn, String userId) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(() -> new PendingRequestNotFoundException(id)))
                .flatMap(request -> {
                    request.setStatus(PendingRequestStatus.COMPLETED.name());
                    request.setAuthToken(authToken);
                    request.setAuthTokenExpiresIn(expiresIn);
                    request.setUserId(userId);
                    return repository.update(request);
                });
    }

    /**
     * Mark a pending request as denied.
     */
    public Single<AAuthPendingRequest> deny(String id) {
        return repository.updateStatus(id, PendingRequestStatus.DENIED.name());
    }

    /**
     * Store a user's clarification question and transition to AWAITING_CLARIFICATION.
     * Per spec Section 7.3.4, max 5 rounds.
     */
    public Single<AAuthPendingRequest> askClarification(String id, String question) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(() -> new PendingRequestNotFoundException(id)))
                .flatMap(request -> {
                    if (!request.isClarificationSupported()) {
                        return Single.error(new ClarificationNotSupportedException());
                    }
                    if (request.getClarificationRoundCount() >= MAX_CLARIFICATION_ROUNDS) {
                        return Single.error(new MaxClarificationRoundsException());
                    }
                    request.setStatus(PendingRequestStatus.AWAITING_CLARIFICATION.name());
                    request.setClarification(question);
                    request.setClarificationResponse(null);
                    request.setClarificationRoundCount(request.getClarificationRoundCount() + 1);
                    return repository.update(request);
                });
    }

    /**
     * Store the agent's clarification response and transition back to INTERACTING.
     */
    public Single<AAuthPendingRequest> respondClarification(String id, String agentJkt, String response) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(() -> new PendingRequestNotFoundException(id)))
                .flatMap(request -> {
                    if (!Objects.equals(request.getAgentJkt(), agentJkt)) {
                        return Single.error(new SecurityException("Agent key mismatch"));
                    }
                    if (!PendingRequestStatus.AWAITING_CLARIFICATION.name().equals(request.getStatus())) {
                        return Single.error(new IllegalStateException("Not awaiting clarification"));
                    }
                    request.setStatus(PendingRequestStatus.INTERACTING.name());
                    request.setClarificationResponse(response);
                    return repository.update(request);
                });
    }

    /**
     * Cancel a pending request (agent sends DELETE). Per spec Section 7.3.3.3.
     */
    public Completable cancel(String id, String agentJkt) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(() -> new PendingRequestNotFoundException(id)))
                .flatMapCompletable(request -> {
                    if (!Objects.equals(request.getAgentJkt(), agentJkt)) {
                        return Completable.error(new SecurityException("Agent key mismatch"));
                    }
                    request.setStatus(PendingRequestStatus.CANCELLED.name());
                    return repository.update(request).ignoreElement();
                });
    }

    /**
     * Thrown when clarification is attempted but the agent didn't declare support.
     */
    public static class ClarificationNotSupportedException extends RuntimeException {
        public ClarificationNotSupportedException() {
            super("Agent did not declare clarification capability");
        }
    }

    /**
     * Thrown when the maximum number of clarification rounds is exceeded.
     */
    public static class MaxClarificationRoundsException extends RuntimeException {
        public MaxClarificationRoundsException() {
            super("Maximum clarification rounds (" + MAX_CLARIFICATION_ROUNDS + ") exceeded");
        }
    }

    /**
     * Generate a short, human-readable interaction code (e.g. "ABCD-1234").
     */
    private String generateInteractionCode() {
        SecureRandom random = new SecureRandom();
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I or O to avoid confusion
        String digits = "0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(letters.charAt(random.nextInt(letters.length())));
        sb.append('-');
        for (int i = 0; i < 4; i++) sb.append(digits.charAt(random.nextInt(digits.length())));
        return sb.toString();
    }

    private String serializePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Thrown when the agent polls too fast (below the minimum interval).
     */
    public static class TooFastException extends RuntimeException {
        public TooFastException() {
            super("Polling too fast");
        }
    }
}
