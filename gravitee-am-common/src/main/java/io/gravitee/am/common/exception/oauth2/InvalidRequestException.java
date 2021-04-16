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
 * invalid_request
 *          The request is missing a required parameter, includes an
 *          unsupported parameter or parameter value, repeats the same
 *          parameter, uses more than one method for including an access
 *          token, or is otherwise malformed.  The resource server SHOULD
 *          respond with the HTTP 400 (Bad Request) status code.
 *
 * See <a href="https://tools.ietf.org/html/rfc6750#section-3.1">3.1. Error Codes</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidRequestException extends OAuth2Exception {

    public InvalidRequestException() {
        super();
    }

    public InvalidRequestException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_request";
    }
}
