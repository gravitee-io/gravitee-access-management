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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Generates pairwise subject identifiers directed at a specific Agent Server
 * (draft-hardt-aauth-bootstrap §6.4 / §11.1).
 * <p>
 * The generated {@code sub} value is:
 * <ul>
 *   <li><b>Deterministic</b>: same {@code (userId, agentServerUrl)} on the same domain
 *       always produces the same {@code sub} (so re-bootstrapping is idempotent at the AS).</li>
 *   <li><b>Opaque</b>: the AS cannot derive the user's internal id from the {@code sub}.</li>
 *   <li><b>Directed</b>: different Agent Servers receive different {@code sub} values for the
 *       same user (preventing cross-AS user correlation).</li>
 *   <li><b>Domain-scoped</b>: the same user/AS pair on different security domains gets
 *       different {@code sub}s.</li>
 * </ul>
 * <p>
 * The hash input mixes {@link #masterSalt} (an AM-wide secret from configuration) with the
 * security domain id, the user id, and the agent server URL. The master salt is what makes
 * the result non-derivable by anyone who only knows the public domain id.
 */
public class PairwiseSubjectGenerator {

    /** Byte length of the truncated SHA-256 output (192 bits / 32 base64url chars). */
    private static final int PAIRWISE_SUB_BYTES = 24;

    private final String masterSalt;
    private final String domainId;

    /**
     * @param masterSalt AM-wide secret (from {@code aauth.pairwise.subject.salt}).
     *                   MUST NOT be {@code null}; pass an empty string only in tests.
     * @param domainId   the security domain identifier (mixed into the hash to scope subs per domain).
     */
    public PairwiseSubjectGenerator(String masterSalt, String domainId) {
        if (masterSalt == null) {
            throw new IllegalArgumentException("masterSalt must not be null");
        }
        if (domainId == null || domainId.isBlank()) {
            throw new IllegalArgumentException("domainId must not be blank");
        }
        this.masterSalt = masterSalt;
        this.domainId = domainId;
    }

    /**
     * Compute the pairwise subject identifier for the given input on this generator's domain.
     * Returns a 32-character URL-safe base64 string.
     *
     * @param userId         the AM internal user id
     * @param identityId     the id of the user's identity selected by B2B parameters
     *                       ({@code domain_hint}, {@code tenant}). Pass {@code null} for the
     *                       user's default/primary identity — this is the canonical value
     *                       for non-B2B flows and MUST stay {@code null} when the user has
     *                       only one identity. Hashing treats {@code null} as the empty
     *                       string; switching from null to a real id later (or vice versa)
     *                       will produce a different sub.
     * @param agentServerUrl the audience the bootstrap_token is directed at
     */
    public String generate(String userId, String identityId, String agentServerUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Domain-scoped salt prefix: domain id + master salt make the output
            // non-derivable from the public (userId, agentServerUrl) pair alone.
            // identityId is reserved for future B2B identity selection — pass null today.
            digest.update(domainId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(masterSalt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(userId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            if (identityId != null) {
                digest.update(identityId.getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) '|');
            digest.update(agentServerUrl.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();

            byte[] truncated = new byte[PAIRWISE_SUB_BYTES];
            System.arraycopy(hash, 0, truncated, 0, PAIRWISE_SUB_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
