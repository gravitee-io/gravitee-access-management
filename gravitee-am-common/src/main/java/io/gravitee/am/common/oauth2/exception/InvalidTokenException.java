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
package io.gravitee.am.common.oauth2.exception;

import io.gravitee.common.http.HttpStatusCode;

/**
 * invalid_token
 *          The access token provided is expired, revoked, malformed, or
 *          invalid for other reasons.  The resource SHOULD respond with
 *          the HTTP 401 (Unauthorized) status code.  The client MAY
 *          request a new access token and retry the protected resource
 *          request.
 *
 * See <a href="https://tools.ietf.org/html/rfc6750#section-3.1">3.1. Error Codes</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidTokenException extends OAuth2Exception {

    public InvalidTokenException() {
    }

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_token";
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.UNAUTHORIZED_401;
    }
}
