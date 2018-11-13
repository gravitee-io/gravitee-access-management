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

import io.gravitee.common.http.HttpStatusCode;

/**
 * There is no specification about an invalid token.
 * We are assuming that this exception must end with a 401 response status.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
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
