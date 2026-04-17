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
package io.gravitee.am.gateway.handler.aauth.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects replayed HTTP Message Signatures by tracking (jwkThumbprint, created) pairs.
 * Per the HTTP Signature Headers spec, servers MUST reject duplicate pairs.
 *
 * The window is 120 seconds (±60s from the signature validity window).
 * Entries are lazily evicted when older than the window.
 */
public class ReplayDetector {

    private static final long WINDOW_SECONDS = 120;
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    /**
     * Check if this exact signature has been seen before.
     * Uses (thumbprint, created, signatureBytes) to distinguish different requests
     * signed with the same key in the same second.
     *
     * @param jwkThumbprint  the JWK Thumbprint of the signing key
     * @param created        the created timestamp from the signature
     * @param signatureBytes the raw signature bytes (unique per request)
     * @throws SignatureVerificationException if this is a replay
     */
    public void check(String jwkThumbprint, long created, byte[] signatureBytes) throws SignatureVerificationException {
        evictStale();

        String key = computeKey(jwkThumbprint, created, signatureBytes);
        Long previous = seen.putIfAbsent(key, Instant.now().getEpochSecond());

        if (previous != null) {
            throw new SignatureVerificationException("invalid_signature");
        }
    }


    private void evictStale() {
        long cutoff = Instant.now().getEpochSecond() - WINDOW_SECONDS;
        seen.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private String computeKey(String thumbprint, long created, byte[] signatureBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(thumbprint.getBytes(StandardCharsets.UTF_8));
            md.update(Long.toString(created).getBytes(StandardCharsets.UTF_8));
            md.update(signatureBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Visible for testing: returns the current number of tracked entries.
     */
    int size() {
        return seen.size();
    }
}
