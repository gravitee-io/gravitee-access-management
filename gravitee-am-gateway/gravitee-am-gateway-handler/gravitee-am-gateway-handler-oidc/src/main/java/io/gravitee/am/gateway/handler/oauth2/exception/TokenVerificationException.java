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
package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * Signals that a token's cryptographic verification failed (e.g., signature mismatch,
 * missing certificate). This is a subtype of {@link InvalidGrantException} so that
 * OAuth2 error handling remains unchanged ({@code invalid_grant}), but decorators
 * can distinguish verification failures from explicit token rejections (expired, not-yet-valid).
 *
 * @author GraviteeSource Team
 */
public class TokenVerificationException extends InvalidGrantException {

    public TokenVerificationException(String message) {
        super(message);
    }
}
