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
 * The authorization server does not support obtaining an access token using this method.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2.2.1">4.2.2.1. Error Response</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UnsupportedResponseModeException extends OAuth2Exception {

    public UnsupportedResponseModeException() {
        super();
    }

    public UnsupportedResponseModeException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "unsupported_response_mode";
    }
}
