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
 * Raised when {@link BootstrapTokenMinter} cannot produce a verifiable bootstrap_token —
 * typically because no asymmetric (RS256/ES256) signing certificate is configured for the
 * domain. Surfaced to the operator as a 500-class error; it is a misconfiguration, not a
 * client mistake.
 */
public class BootstrapTokenSigningException extends RuntimeException {

    public BootstrapTokenSigningException(String message) {
        super(message);
    }

    public BootstrapTokenSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
