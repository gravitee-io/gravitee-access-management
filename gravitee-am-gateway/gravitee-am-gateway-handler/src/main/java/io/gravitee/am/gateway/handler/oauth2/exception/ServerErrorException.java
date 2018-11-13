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
 * The authorization server encountered an unexpected condition that prevented it from fulfilling the request.
 * (This error code is needed because a 500 Internal Server Error HTTP status code cannot be returned to the client via an HTTP redirect.)
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServerErrorException extends OAuth2Exception {

    public ServerErrorException() {
        super();
    }

    public ServerErrorException(String message) {
        super(message);
    }

    public ServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "server_error";
    }
}
