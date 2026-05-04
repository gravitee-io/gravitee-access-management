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

/**
 * Raised by {@link AAuthBootstrapService#create} when the Agent Server metadata
 * document cannot be retrieved or fails validation. The bootstrap consent screen
 * relies on the AS metadata for identity disclosure to the user
 * (draft-hardt-aauth-bootstrap §6.3, §11.2 consent phishing); rather than render
 * a consent screen with no verifiable AS identity, the PS fails the request.
 * <p>
 * The {@link #errorCode} is the {@code error_description} string the endpoint
 * surfaces in the {@code 400 invalid_request} response, so AS operators can
 * distinguish unreachable metadata from issuer mismatch.
 */
public class BootstrapMetadataException extends RuntimeException {

    /** {@code error_description} payload returned to the client. */
    public static final String ERR_UNREACHABLE = "agent_server_metadata_unreachable";

    /** {@code error_description} payload returned to the client. */
    public static final String ERR_ISSUER_MISMATCH = "agent_server_metadata_issuer_mismatch";

    private final String errorCode;

    public BootstrapMetadataException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public BootstrapMetadataException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
