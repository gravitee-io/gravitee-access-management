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
package io.gravitee.am.service.exception;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;

/**
 * The value of one or more redirect_uris is invalid.
 *
 * https://openid.net/specs/openid-connect-registration-1_0.html#RegistrationError
 *
 * @author Ashraful Hasan
 * @author GraviteeSource Team
 */
public class InvalidTargetUrlException extends OAuth2Exception {

    public InvalidTargetUrlException() {
        super("Missing or invalid target_url.");
    }

    public InvalidTargetUrlException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_target_url";
    }
}
