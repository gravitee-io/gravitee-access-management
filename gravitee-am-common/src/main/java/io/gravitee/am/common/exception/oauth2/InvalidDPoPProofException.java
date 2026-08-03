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
package io.gravitee.am.common.exception.oauth2;

import io.gravitee.common.http.HttpStatusCode;

import static io.gravitee.am.common.utils.ConstantKeys.INVALID_DPOP_PROOF;

/**
 * invalid_dpop_proof
 *          The DPoP proof JWT presented on the request is missing, malformed, or fails one of
 *          the RFC 9449 §4.3 validation checks.
 *
 * <p>The token endpoint answers such failures with an HTTP 400 (this exception's default status).
 * The resource-enforcement path answers with an HTTP 401 and a {@code DPoP} {@code WWW-Authenticate}
 * challenge; it therefore translates this error code rather than relying on the default status.</p>
 *
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449 — OAuth 2.0 Demonstrating Proof of Possession (DPoP)</a>
 *
 * @author GraviteeSource Team
 */
public class InvalidDPoPProofException extends OAuth2Exception {

    public InvalidDPoPProofException(String message) {
        super(message);
    }

    public InvalidDPoPProofException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return INVALID_DPOP_PROOF;
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.BAD_REQUEST_400;
    }
}
