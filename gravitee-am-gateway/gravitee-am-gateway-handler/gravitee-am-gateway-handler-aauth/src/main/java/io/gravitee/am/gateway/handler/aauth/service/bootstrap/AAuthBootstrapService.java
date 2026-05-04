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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapBindingRepository;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for the AAUTH Bootstrap ceremony (PS-side).
 * <p>
 * Manages the lifecycle of bootstrap requests from creation through
 * user approval/denial, token minting, and binding announcement.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapService {

    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final int RETENTION_BUFFER_SECONDS = 900; // 15 min retention after TTL

    private final AAuthBootstrapRequestRepository requestRepository;
    private final AAuthBootstrapBindingRepository bindingRepository;
    private final BootstrapTokenMinter tokenMinter;
    private final PairwiseSubjectGenerator pairwiseGenerator;
    private final AgentMetadataFetcher metadataFetcher;

    /**
     * Create a new bootstrap request.
     * <p>
     * Fails closed if the Agent Server metadata cannot be fetched or its {@code issuer}
     * does not match the requested {@code agent_server} URL — without a verified AS
     * identity, the consent screen can't honestly tell the user who they're approving
     * (draft-hardt-aauth-bootstrap §6.3 / §11.2).
     */
    public Single<AAuthBootstrapRequest> create(String domain, String agentServerUrl,
                                                  String ephemeralKeyJwk, String ephemeralKeyThumbprint,
                                                  String domainHint, String loginHint, String tenant,
                                                  int ttlSeconds) {
        AgentMetadata metadata;
        try {
            metadata = metadataFetcher.fetchMetadata(agentServerUrl);
        } catch (Exception e) {
            log.warn("Bootstrap rejected: agent server metadata unreachable for {}: {}",
                    agentServerUrl, e.getMessage());
            return Single.error(new BootstrapMetadataException(
                    BootstrapMetadataException.ERR_UNREACHABLE,
                    "Agent server metadata could not be retrieved from " + agentServerUrl, e));
        }
        if (metadata == null) {
            return Single.error(new BootstrapMetadataException(
                    BootstrapMetadataException.ERR_UNREACHABLE,
                    "Agent server metadata could not be retrieved from " + agentServerUrl));
        }
        if (metadata.issuer() == null || !metadata.issuer().equals(agentServerUrl)) {
            log.warn("Bootstrap rejected: agent server metadata issuer={} does not match requested agent_server={}",
                    metadata.issuer(), agentServerUrl);
            return Single.error(new BootstrapMetadataException(
                    BootstrapMetadataException.ERR_ISSUER_MISMATCH,
                    "Agent server metadata issuer does not match the requested agent_server URL"));
        }

        // Display values for the consent screen. clientName is OPTIONAL in the spec;
        // fall back to the host portion of the URL so the user always sees a stable label.
        // logoUri is also OPTIONAL — null means "render no logo" (no generic placeholder
        // that could be mistaken for a verified identity badge).
        String agentServerName = metadata.clientName() != null && !metadata.clientName().isBlank()
                ? metadata.clientName()
                : hostOf(agentServerUrl);
        String agentServerLogoUri = metadata.logoUri();

        AAuthBootstrapRequest request = new AAuthBootstrapRequest();
        request.setId(UUID.randomUUID().toString());
        request.setStatus(PendingRequestStatus.PENDING.name());
        request.setDomain(domain);
        request.setAgentServerUrl(agentServerUrl);
        request.setAgentServerName(agentServerName);
        request.setAgentServerLogoUri(agentServerLogoUri);
        request.setEphemeralKeyJwk(ephemeralKeyJwk);
        request.setEphemeralKeyThumbprint(ephemeralKeyThumbprint);
        request.setInteractionCode(generateInteractionCode());
        request.setDomainHint(domainHint);
        request.setLoginHint(loginHint);
        request.setTenant(tenant);

        Date now = new Date();
        request.setCreatedAt(now);
        request.setLastAccessAt(now);
        request.setExpireAt(new Date(now.getTime() + (ttlSeconds + RETENTION_BUFFER_SECONDS) * 1000L));

        return requestRepository.create(request);
    }

    /**
     * Poll for bootstrap request status.
     * Rate-limits (5s minimum between polls).
     * Verifies ephemeral key thumbprint matches.
     * On COMPLETED: consume-once (delete after returning bootstrap_token).
     *
     * @return the bootstrap request, or empty if not found/expired
     */
    public Maybe<AAuthBootstrapRequest> poll(String id, String ephemeralKeyThumbprint) {
        return requestRepository.findById(id)
                .flatMap(request -> {
                    if (!Objects.equals(request.getEphemeralKeyThumbprint(), ephemeralKeyThumbprint)) {
                        return Maybe.error(new SecurityException("Ephemeral key thumbprint mismatch"));
                    }

                    long elapsed = System.currentTimeMillis() - request.getLastAccessAt().getTime();
                    if (elapsed < DEFAULT_POLL_INTERVAL_SECONDS * 1000L) {
                        return Maybe.error(new TooFastException());
                    }

                    // On COMPLETED, return the bootstrap_token but do NOT delete the record.
                    // The record is still needed for the announcement step (Section 6.7).
                    // Cleanup happens via TTL expiration.
                    if (PendingRequestStatus.COMPLETED.name().equals(request.getStatus())) {
                        return Maybe.just(request);
                    }

                    request.setLastAccessAt(new Date());
                    return requestRepository.update(request).toMaybe();
                });
    }

    /**
     * Mark a bootstrap request as INTERACTING and set the userId.
     */
    public Completable markInteracting(String id, String userId) {
        return requestRepository.findById(id)
                .switchIfEmpty(Single.error(() -> new BootstrapRequestNotFoundException(id)))
                .flatMap(request -> {
                    request.setStatus(PendingRequestStatus.INTERACTING.name());
                    request.setUserId(userId);
                    return requestRepository.update(request);
                })
                .ignoreElement();
    }

    /**
     * Approve a bootstrap request: generate pairwiseSub, mint bootstrap_token, set COMPLETED.
     * <p>
     * The pairwise salt is configured at bean construction time in {@link PairwiseSubjectGenerator}
     * (sourced from {@code aauth.pairwise.subject.salt}) — the caller does not pass it here.
     */
    public Single<AAuthBootstrapRequest> approve(String id, String userId, String psIssuerUrl) {
        return requestRepository.findById(id)
                .switchIfEmpty(Single.error(() -> new BootstrapRequestNotFoundException(id)))
                .flatMap(request -> {
                    // identityId stays null until B2B identity selection lands in a future phase.
                    // Threading the parameter now keeps the hash format stable across that change
                    // — when B2B picks a non-default identity, that case will pass a non-null id;
                    // the default-identity case must keep passing null to preserve existing subs.
                    String pairwiseSub = pairwiseGenerator.generate(userId, null, request.getAgentServerUrl());
                    request.setPairwiseSub(pairwiseSub);
                    request.setUserId(userId);

                    return tokenMinter.mint(psIssuerUrl, request.getAgentServerUrl(),
                                    pairwiseSub, request.getEphemeralKeyJwk())
                            .flatMap(bootstrapToken -> {
                                request.setBootstrapToken(bootstrapToken);
                                request.setStatus(PendingRequestStatus.COMPLETED.name());
                                return requestRepository.update(request);
                            });
                });
    }

    /**
     * Deny a bootstrap request.
     */
    public Single<AAuthBootstrapRequest> deny(String id) {
        return requestRepository.updateStatus(id, PendingRequestStatus.DENIED.name());
    }

    /**
     * Record a bootstrap binding after the Agent Server announces completion
     * (draft-hardt-aauth-bootstrap §6.7).
     * <p>
     * Four outcomes:
     * <ul>
     *   <li>No bootstrap request matches the ephemeral thumbprint →
     *       {@link BootstrapRequestNotFoundException} (caller maps to 404).</li>
     *   <li>A binding for {@code (userId, agentServerUrl)} already exists with the same
     *       {@code agentIdentifier} → idempotent: refresh {@code updatedAt}, complete normally
     *       (caller returns 204).</li>
     *   <li>A binding for {@code (userId, agentServerUrl)} already exists with a *different*
     *       {@code agentIdentifier} → {@link BootstrapBindingConflictException} (caller maps to
     *       409). The Agent Server must not give two different agent identities for the same
     *       (PS user, AS) pair.</li>
     *   <li>No existing binding → create one.</li>
     * </ul>
     */
    public Completable announce(String domain, String ephemeralKeyThumbprint,
                                String agentIdentifier, String agentServerUrl) {
        return requestRepository.findByEphemeralKeyThumbprint(ephemeralKeyThumbprint)
                .switchIfEmpty(Single.error(() ->
                        new BootstrapRequestNotFoundException("thumbprint=" + ephemeralKeyThumbprint)))
                .flatMapCompletable(request ->
                        bindingRepository.findByDomainAndAgentServerUrlAndUserId(
                                        domain, agentServerUrl, request.getUserId())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .flatMapCompletable(maybeExisting -> {
                                    if (maybeExisting.isPresent()) {
                                        AAuthBootstrapBinding existing = maybeExisting.get();
                                        if (Objects.equals(existing.getAgentIdentifier(), agentIdentifier)) {
                                            existing.setUpdatedAt(new Date());
                                            return bindingRepository.update(existing).ignoreElement();
                                        }
                                        return Completable.error(new BootstrapBindingConflictException(
                                                request.getUserId(), agentServerUrl,
                                                existing.getAgentIdentifier(), agentIdentifier));
                                    }
                                    return bindingRepository.create(buildNewBinding(
                                                    domain, request, agentServerUrl, agentIdentifier))
                                            .ignoreElement();
                                }));
    }

    private static AAuthBootstrapBinding buildNewBinding(String domain,
                                                         AAuthBootstrapRequest request,
                                                         String agentServerUrl,
                                                         String agentIdentifier) {
        AAuthBootstrapBinding binding = new AAuthBootstrapBinding();
        binding.setId(UUID.randomUUID().toString());
        binding.setDomain(domain);
        binding.setUserId(request.getUserId());
        binding.setAgentServerUrl(agentServerUrl);
        binding.setAgentIdentifier(agentIdentifier);
        binding.setPairwiseSub(request.getPairwiseSub());
        Date now = new Date();
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        return binding;
    }

    /**
     * Find bootstrap request by interaction code (for the consent flow).
     */
    public Maybe<AAuthBootstrapRequest> findByInteractionCode(String code) {
        return requestRepository.findByInteractionCode(code);
    }

    /**
     * Best-effort hostname extraction for the consent-screen fallback when
     * {@code client_name} is missing. Returns the URL itself if parsing fails —
     * we already validated the URL upstream, so this branch is defensive only.
     */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
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
}
