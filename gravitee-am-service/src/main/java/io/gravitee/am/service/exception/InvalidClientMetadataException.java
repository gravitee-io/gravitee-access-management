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
 * The value of one of the Client Metadata fields is invalid and the server has rejected this request.
 * Note that an Authorization Server MAY choose to substitute a valid value for any requested parameter of a Client's Metadata.
 *
 * https://openid.net/specs/openid-connect-registration-1_0.html#RegistrationError
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 */
public class InvalidClientMetadataException extends OAuth2Exception {

    public InvalidClientMetadataException() {
        super("One of the Client Metadata value is invalid.");
    }

    public InvalidClientMetadataException(String message) { super(message); }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_client_metadata";
    }
}
