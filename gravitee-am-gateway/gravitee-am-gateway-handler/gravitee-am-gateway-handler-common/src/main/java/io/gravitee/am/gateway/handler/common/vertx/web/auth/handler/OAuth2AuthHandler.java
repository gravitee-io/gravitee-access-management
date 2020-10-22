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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.handler;

import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl.OAuth2AuthHandlerImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;

/**
 * See <a href="https://tools.ietf.org/html/rfc6750">The OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
 *
 * Default implementation with the Authorization Request Header Field
 *
 * When sending the access token in the "Authorization" request header
 *    field defined by HTTP/1.1 [RFC2617], the client uses the "Bearer"
 *    authentication scheme to transmit the access token.
 *
 * See <a href="https://tools.ietf.org/html/rfc6750#section-2.1>2.1. Authorization Request Header Field</a>
 *
 * When a request fails, the resource server responds using the
 *    appropriate HTTP status code (typically, 400, 401, 403, or 405) and
 *    includes one of the following error codes in the response:
 *
 *    invalid_request
 *          The request is missing a required parameter, includes an
 *          unsupported parameter or parameter value, repeats the same
 *          parameter, uses more than one method for including an access
 *          token, or is otherwise malformed.  The resource server SHOULD
 *          respond with the HTTP 400 (Bad Request) status code.
 *
 *    invalid_token
 *          The access token provided is expired, revoked, malformed, or
 *          invalid for other reasons.  The resource SHOULD respond with
 *          the HTTP 401 (Unauthorized) status code.  The client MAY
 *          request a new access token and retry the protected resource
 *          request.
 *
 *    insufficient_scope
 *          The request requires higher privileges than provided by the
 *          access token.  The resource server SHOULD respond with the HTTP
 *          403 (Forbidden) status code and MAY include the "scope"
 *          attribute with the scope necessary to access the protected
 *          resource.
 *
 *    If the request lacks any authentication information (e.g., the client
 *    was unaware that authentication is necessary or attempted using an
 *    unsupported authentication method), the resource server SHOULD NOT
 *    include an error code or other error information.
 *
 *    For example:
 *
 *      HTTP/1.1 401 Unauthorized
 *      WWW-Authenticate: Bearer realm="example"
 *
 * See <a href="https://tools.ietf.org/html/rfc6750#section-3.1">3.1. Error Codes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OAuth2AuthHandler extends AuthHandler {

    /**
     * Extract JWT String representation OAuth 2.0 token and add it to the execution context
     * @param extractRawToken
     */
    void extractRawToken(boolean extractRawToken);

    /**
     * Extract OAuth 2.0 token and add it to the execution context
     * @param extractToken
     */
    void extractToken(boolean extractToken);

    /**
     * Extract OAuth 2.0 client and add it to the execution context
     * @param extractClient
     */
    void extractClient(boolean extractClient);

    /**
     * Current web resource must be accessed with the OAuth 2.0 token generated for an end user
     * @param forceEndUserToken
     */
    void forceEndUserToken(boolean forceEndUserToken);

    /**
     * Current web resource must be accessed with the OAuth 2.0 token generated for a client
     * @param forceClientToken
     */
    void forceClientToken(boolean forceClientToken);

    /**
     * Current resource must match the OAuth 2.0 token 'sub' claim
     * @param selfResource
     * @param resourceParameter
     */
    void selfResource(boolean selfResource, String resourceParameter);

    /**
     * Current resource must match the OAuth 2.0 token 'sub' claim
     * @param selfResource
     * @param resourceParameter
     * @param requiredScope
     */
    void selfResource(boolean selfResource, String resourceParameter, String requiredScope);

    /**
     * Only verify JWT and do not call database for extra verifications (token revocation)
     * @param offlineVerification
     */
    void offlineVerification(boolean offlineVerification);

    static OAuth2AuthHandler create(OAuth2AuthProvider oAuth2AuthProvider) {
        return new OAuth2AuthHandlerImpl(oAuth2AuthProvider);
    }

    static OAuth2AuthHandler create(OAuth2AuthProvider oAuth2AuthProvider, String requiredScope) {
        return new OAuth2AuthHandlerImpl(oAuth2AuthProvider, requiredScope);
    }
}
