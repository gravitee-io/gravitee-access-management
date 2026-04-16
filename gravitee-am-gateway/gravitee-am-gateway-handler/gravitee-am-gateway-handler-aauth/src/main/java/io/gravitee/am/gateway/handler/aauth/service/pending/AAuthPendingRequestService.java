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
    private static final int RETENTION_BUFFER_SECONDS = 900; // 15 min retention after TTL

    private final AAuthPendingRequestRepository repository;

    /**
     * Create a new pending request for the deferred authorization flow.
     */
    public Single<AAuthPendingRequest> create(String domain, String agentId, String agentSub,
                                                String agentJkt, PublicKey agentPublicKey,
                                                String applicationId, String resourceIss,
                                                String scope, String justification,
                                                String psIssuerUrl, int ttlSeconds) {
        AAuthPendingRequest request = new AAuthPendingRequest();
        request.setId(UUID.randomUUID().toString());
        request.setStatus(PendingRequestStatus.PENDING.name());
        request.setDomain(domain);
        request.setAgentId(agentId);
        request.setAgentSub(agentSub);
        request.setAgentJkt(agentJkt);
        request.setAgentPublicKey(serializePublicKey(agentPublicKey));
        request.setApplicationId(applicationId);
        request.setResourceIss(resourceIss);
        request.setScope(scope);
        request.setJustification(justification);
        request.setInteractionCode(generateInteractionCode());
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
