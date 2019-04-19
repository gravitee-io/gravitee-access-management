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
 * insufficient_scope
 *          The request requires higher privileges than provided by the
 *          access token.  The resource server SHOULD respond with the HTTP
 *          403 (Forbidden) status code and MAY include the "scope"
 *          attribute with the scope necessary to access the protected
 *          resource.
 *
 * See <a href="https://tools.ietf.org/html/rfc6750#section-3">3.1. Error Codes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InsufficientScopeException extends InvalidTokenException {

    public InsufficientScopeException() {
    }

    public InsufficientScopeException(String message) {
        super(message);
    }

    public InsufficientScopeException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "insufficient_scope";
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.FORBIDDEN_403;
    }
}
