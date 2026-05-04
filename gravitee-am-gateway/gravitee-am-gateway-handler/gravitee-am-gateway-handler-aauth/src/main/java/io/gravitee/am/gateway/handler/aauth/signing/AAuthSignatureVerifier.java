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

import io.gravitee.am.gateway.handler.aauth.signing.schemes.ResolvedKey;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.SignatureSchemeFactory;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates HTTP Message Signature verification per the AAUTH protocol spec.
 * <p>
 * Verification steps:
 * <ol>
 *   <li>Extract Signature, Signature-Input, Signature-Key headers</li>
 *   <li>Parse Signature-Input — validate required covered components</li>
 *   <li>Validate {@code created} timestamp within ±60s of server time</li>
 *   <li>Determine algorithm from key</li>
 *   <li>Resolve public key via {@link io.gravitee.am.gateway.handler.aauth.signing.schemes.SignatureScheme}</li>
 *   <li>Build signature base, verify signature</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthSignatureVerifier {

    private static final long SIGNATURE_WINDOW_SECONDS = 60;

    private final SignatureSchemeFactory schemeFactory;
    private final ReplayDetector replayDetector;

    /**
     * Verify the HTTP Message Signature on the given request.
     *
     * @param request the HTTP request
     * @param body    the request body (may be null for GET requests)
     * @return the verification result containing the public key and JWK thumbprint
     * @throws SignatureVerificationException if verification fails
     */
    public VerificationResult verify(HttpServerRequest request, byte[] body)
            throws SignatureVerificationException {

        // Step 1: Extract headers
        String signatureHeader = request.getHeader("Signature");
        String signatureInputHeader = request.getHeader("Signature-Input");
        String signatureKeyHeader = request.getHeader("Signature-Key");

        if (signatureHeader == null || signatureInputHeader == null || signatureKeyHeader == null) {
            throw new SignatureVerificationException("invalid_request");
        }

        // Step 2: Parse Signature-Input and Signature-Key
        SignatureInputInfo inputInfo = SignatureInputParser.parse(signatureInputHeader);
        SignatureKeyInfo keyInfo = SignatureKeyParser.parse(signatureKeyHeader);

        // Validate label consistency
        if (!inputInfo.label().equals(keyInfo.label())) {
            throw new SignatureVerificationException("invalid_signature");
        }

        // Step 3: Validate timestamp
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - inputInfo.created()) > SIGNATURE_WINDOW_SECONDS) {
            throw new SignatureVerificationException("invalid_signature");
        }

        // Step 4 + 5: Resolve public key via scheme
        ResolvedKey resolvedKey = schemeFactory.get(keyInfo.scheme()).resolve(keyInfo);

        // Validate Content-Digest if present
        String contentDigest = request.getHeader("Content-Digest");
        if (contentDigest != null && body != null) {
            ContentDigestValidator.validate(contentDigest, body);
        }

        // Step 6: Build signature base and verify
        Map<String, String> headers = extractHeaders(request);
        byte[] signatureBase = SignatureBaseBuilder.build(request, headers, inputInfo);

        // Decode the Signature header: sig=:<base64>:
        byte[] signatureBytes = decodeSignature(signatureHeader, inputInfo.label());

        verifySignature(resolvedKey, signatureBase, signatureBytes);

        // Replay detection — uses signature bytes to distinguish different requests
        // signed with the same key in the same second
        replayDetector.check(resolvedKey.jwkThumbprint(), inputInfo.created(), signatureBytes);

        return new VerificationResult(
                keyInfo.scheme(),
                keyInfo.label(),
                resolvedKey.publicKey(),
                resolvedKey.jwkThumbprint(),
                resolvedKey.agentServerUrl(),
                resolvedKey.agentIdentifier(),
                resolvedKey.agentTokenPs()
        );
    }

    private Map<String, String> extractHeaders(HttpServerRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers()) {
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return headers;
    }

    private byte[] decodeSignature(String signatureHeader, String expectedLabel) throws SignatureVerificationException {
        // Format: sig=:<base64>:
        int eqIdx = signatureHeader.indexOf('=');
        if (eqIdx <= 0) {
            throw new SignatureVerificationException("invalid_signature");
        }

        String label = signatureHeader.substring(0, eqIdx).trim();
        if (!label.equals(expectedLabel)) {
            throw new SignatureVerificationException("invalid_signature");
        }

        String value = signatureHeader.substring(eqIdx + 1).trim();
        // Strip ':' delimiters
        if (value.startsWith(":") && value.endsWith(":")) {
            value = value.substring(1, value.length() - 1);
        }

        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new SignatureVerificationException("invalid_signature");
        }
    }

    private void verifySignature(ResolvedKey resolvedKey, byte[] signatureBase, byte[] signatureBytes)
            throws SignatureVerificationException {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Signature base ({} bytes):\n{}", signatureBase.length,
                        new String(signatureBase, java.nio.charset.StandardCharsets.UTF_8));
            }

            Signature sig = Signature.getInstance(resolvedKey.algorithm());
            sig.initVerify(resolvedKey.publicKey());
            sig.update(signatureBase);

            if (!sig.verify(signatureBytes)) {
                log.debug("Signature verification returned false");
                throw new SignatureVerificationException("invalid_signature");
            }
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Signature verification failed: {}", e.getMessage(), e);
            throw new SignatureVerificationException("invalid_signature");
        }
    }
}
