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
package io.gravitee.am.gateway.handler.aauth.signing.schemes;

import java.security.PublicKey;

/**
 * Result of resolving a public key from a Signature-Key header via a scheme.
 *
 * @param publicKey       the resolved public key
 * @param algorithm       Java algorithm name for signature verification (e.g. "Ed25519", "SHA256withECDSA")
 * @param jwkThumbprint   RFC 7638 JWK Thumbprint — base64url-encoded SHA-256 hash of the canonical JWK
 * @param agentServerUrl  the agent server URL for JWKS discovery and Application lookup (jwt/jwks_uri schemes),
 *                        or {@code null} for pseudonymous (hwk)
 * @param agentIdentifier the agent identifier in {@code aauth:local@domain} format (jwt scheme only, from jwt.sub),
 *                        or {@code null} when not available
 */
public record ResolvedKey(
        PublicKey publicKey,
        String algorithm,
        String jwkThumbprint,
        String agentServerUrl,
        String agentIdentifier
) {
    /**
     * Constructor for schemes that don't resolve agent identity (e.g. hwk).
     */
    public ResolvedKey(PublicKey publicKey, String algorithm, String jwkThumbprint) {
        this(publicKey, algorithm, jwkThumbprint, null, null);
    }
}
