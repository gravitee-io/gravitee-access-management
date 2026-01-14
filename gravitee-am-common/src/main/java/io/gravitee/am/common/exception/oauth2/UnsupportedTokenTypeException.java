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

/**
 * unsupported_token_type
 *          The authorization server does not support the revocation of the
 *          presented token type.  That is, the client tried to revoke an
 *          access token on a server not supporting this feature.
 *
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.2.2">RFC 8693 Section 2.2.2</a>
 *
 * @author GraviteeSource Team
 */
public class UnsupportedTokenTypeException extends OAuth2Exception {

    public UnsupportedTokenTypeException() {
        super();
    }

    public UnsupportedTokenTypeException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "unsupported_token_type";
    }

}
