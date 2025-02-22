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
 * The provided authorization grant (e.g., authorization code, resource owner credentials) or refresh token is
 * invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-5.2">Error Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidGrantException extends OAuth2Exception {

    public InvalidGrantException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_grant";
    }

}
