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

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;

/**
 * Exception thrown when a token exchange request specifies an invalid or
 * unacceptable target resource or audience.
 *
 * See <a href="https://tools.ietf.org/html/rfc8693#section-2.2.2">RFC 8693 Section 2.2.2 - Error Response</a>
 *
 * @author GraviteeSource Team
 */
public class InvalidTargetException extends OAuth2Exception {

    public InvalidTargetException() {
        super();
    }

    public InvalidTargetException(String message) {
        super(message);
    }

    public InvalidTargetException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_target";
    }

    @Override
    public int getHttpStatusCode() {
        return 400;
    }
}
