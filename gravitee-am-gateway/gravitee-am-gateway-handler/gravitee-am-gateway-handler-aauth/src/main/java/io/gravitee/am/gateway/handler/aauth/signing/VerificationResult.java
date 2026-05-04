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

import java.security.PublicKey;

/**
 * Result of successful HTTP Message Signature verification.
 * Stored in the RoutingContext as "aauth.verification" for downstream handlers.
 *
 * @param scheme           the Signature-Key scheme used (e.g. "hwk", "jwks_uri", "jwt")
 * @param label            the signature label (e.g. "sig")
 * @param publicKey        the verified signer's public key
 * @param jwkThumbprint    RFC 7638 JWK Thumbprint — base64url-encoded SHA-256 hash
 * @param agentServerUrl   the agent server URL for JWKS discovery and Application lookup,
 *                         or {@code null} for pseudonymous (hwk) requests
 * @param agentIdentifier  the agent identifier in {@code aauth:local@domain} format (jwt scheme, from jwt.sub),
 *                         or {@code null} when not available
 * @param agentTokenPs     value of the {@code ps} claim from the naming {@code aa-agent+jwt} (jwt scheme,
 *                         agent token only); {@code null} otherwise. The bootstrap announcement endpoint
 *                         compares this against its own PS URL per draft-hardt-aauth-bootstrap §6.7.
 */
public record VerificationResult(
        String scheme,
        String label,
        PublicKey publicKey,
        String jwkThumbprint,
        String agentServerUrl,
        String agentIdentifier,
        String agentTokenPs
) {
    /**
     * Backward-compatible constructor for callers that don't carry an
     * {@code agent_token.ps} claim (hwk, jwks_uri, or aa-auth+jwt verifications, and tests).
     */
    public VerificationResult(String scheme, String label, PublicKey publicKey,
                              String jwkThumbprint, String agentServerUrl, String agentIdentifier) {
        this(scheme, label, publicKey, jwkThumbprint, agentServerUrl, agentIdentifier, null);
    }
}
