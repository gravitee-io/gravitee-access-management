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
package io.gravitee.am.gateway.handler.oauth2.utils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OAuth2Constants {

    String CLIENT_ID = "client_id";

    String CLIENT_SECRET = "client_secret";

    String STATE = "state";

    String SCOPE = "scope";

    String REDIRECT_URI = "redirect_uri";

    String RESPONSE_TYPE = "response_type";

    String GRANT_TYPE = "grant_type";

    String CODE = "code";

    String TOKEN = "token";

    String IMPLICIT = "implicit";

    String AUTHORIZATION_CODE = "authorization_code";

    String AUTHORIZATION_REQUEST = "authorization_request";

    String USER_OAUTH_APPROVAL = "user_oauth_approval";

    String SCOPE_PREFIX = "scope.";

    String ID_TOKEN = "id_token";

    /**
     * Next constants are defined for PKCE support.
     * See <a href="https://tools.ietf.org/html/rfc7636#section-6.1">PKCE</a>
     */
    String CODE_CHALLENGE = "code_challenge";
    String CODE_CHALLENGE_METHOD = "code_challenge_method";
    String CODE_VERIFIER = "code_verifier";

    /**
     * Next constants are defined for PKCE support.
     * See <a href="https://tools.ietf.org/html/rfc7636#section-6.2.2">PKCE</a>
     */
    String PKCE_METHOD_PLAIN = "plain";
    String PKCE_METHOD_S256 = "S256";
}
