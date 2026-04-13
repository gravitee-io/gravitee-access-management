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

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when HTTP Message Signature verification fails.
 * Carries the error code and optional parameters for the {@code Signature-Error} response header.
 * <p>
 * Error codes per the HTTP Signature Headers spec:
 * <ul>
 *   <li><code>invalid_request</code> — missing Signature, Signature-Input, or Signature-Key headers</li>
 *   <li><code>invalid_input</code> — required covered components missing (includes <code>required_input</code> param)</li>
 *   <li><code>invalid_signature</code> — signature verification failed or timestamp out of window</li>
 *   <li><code>unsupported_algorithm</code> — algorithm not supported (includes <code>supported_algorithms</code> param)</li>
 *   <li><code>invalid_key</code> — key cannot be parsed from Signature-Key header</li>
 *   <li><code>unknown_key</code> — key not found at jwks_uri (used in Phase 3)</li>
 *   <li><code>invalid_jwt</code> — JWT scheme fails verification (used in Phase 8)</li>
 *   <li><code>expired_jwt</code> — JWT has expired (used in Phase 8)</li>
 * </ul>
 */
@Getter
public class SignatureVerificationException extends Exception {

    private final String errorCode;
    private final Map<String, String> params;

    public SignatureVerificationException(String errorCode) {
        this(errorCode, Collections.emptyMap());
    }

    public SignatureVerificationException(String errorCode, Map<String, String> params) {
        super("Signature verification failed: " + errorCode);
        this.errorCode = errorCode;
        this.params = params;
    }

    /**
     * Builds the {@code Signature-Error} header value per the HTTP Signature Headers spec.
     * <p>
     * Examples:
     * <ul>
     *   <li><code>error=invalid_signature</code></li>
     *   <li><code>error=invalid_input; required_input="@method, @authority, @path, signature-key"</code></li>
     *   <li><code>error=unsupported_algorithm; supported_algorithms="EdDSA, ES256"</code></li>
     * </ul>
     */
    public String toSignatureErrorHeader() {
        var sb = new StringBuilder("error=").append(errorCode);
        for (var entry : params.entrySet()) {
            sb.append("; ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
        }
        return sb.toString();
    }
}
