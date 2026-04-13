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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Validates the Content-Digest header per RFC 9530.
 * Supported algorithms: sha-256, sha-512.
 * Format: {@code sha-256=:<base64>:} or {@code sha-512=:<base64>:}
 */
public final class ContentDigestValidator {

    private static final Map<String, String> ALGORITHM_MAP = Map.of(
            "sha-256", "SHA-256",
            "sha-512", "SHA-512"
    );

    private ContentDigestValidator() {
    }

    /**
     * Validate the Content-Digest header against the request body.
     *
     * @param contentDigestHeader the Content-Digest header value
     * @param bodyBytes           the request body bytes
     * @throws SignatureVerificationException if the digest doesn't match or the algorithm is unsupported
     */
    public static void validate(String contentDigestHeader, byte[] bodyBytes) throws SignatureVerificationException {
        if (contentDigestHeader == null || contentDigestHeader.isBlank()) {
            return; // No Content-Digest header — nothing to validate
        }

        // Parse: sha-256=:<base64>:
        int eqIdx = contentDigestHeader.indexOf('=');
        if (eqIdx <= 0) {
            throw new SignatureVerificationException("invalid_signature");
        }

        String algorithm = contentDigestHeader.substring(0, eqIdx).trim().toLowerCase();
        String encodedValue = contentDigestHeader.substring(eqIdx + 1).trim();

        // Strip the ':' delimiters per RFC 9530 byte sequence format
        if (encodedValue.startsWith(":") && encodedValue.endsWith(":")) {
            encodedValue = encodedValue.substring(1, encodedValue.length() - 1);
        }

        String javaAlgorithm = ALGORITHM_MAP.get(algorithm);
        if (javaAlgorithm == null) {
            throw new SignatureVerificationException("unsupported_algorithm",
                    Map.of("supported_algorithms", "sha-256, sha-512"));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(javaAlgorithm);
            byte[] computed = digest.digest(bodyBytes);
            byte[] expected = Base64.getDecoder().decode(encodedValue);

            if (!MessageDigest.isEqual(computed, expected)) {
                throw new SignatureVerificationException("invalid_signature");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureVerificationException("unsupported_algorithm");
        } catch (IllegalArgumentException e) {
            // Invalid base64
            throw new SignatureVerificationException("invalid_signature");
        }
    }
}
