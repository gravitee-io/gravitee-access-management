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
package io.gravitee.am.common.oauth2;

/**
 * OAuth 2.0 Grant Types
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-1.3">1.3. Authorization Grant</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface GrantType {

    /**
     * Client Credentials
     */
    String CLIENT_CREDENTIALS = "client_credentials";

    /**
     * Resource Owner Password Credentials
     */
    String PASSWORD = "password";

    /**
     * Authorization Code
     */
    String AUTHORIZATION_CODE = "authorization_code";

    /**
     * Implicit
     */
    String IMPLICIT = "implicit";

    /**
     * Refresh Token
     */
    String REFRESH_TOKEN = "refresh_token";

    /**
     * Jwt Bearer
     *
     * See <a href="https://tools.ietf.org/html/rfc7523#section-2.1">2.1. Using JWTs as Authorization Grant</a>
     */
    String JWT_BEARER ="urn:ietf:params:oauth:grant-type:jwt-bearer";

    /**
     * SAML 2.0 Bearer
     *
     * See <a href="https://tools.ietf.org/html/rfc7522#section-2.1">2.1. Using SAML Assertions as Authorization Grants</a>
     */
    String SAML2_BEARER ="urn:ietf:params:oauth:grant-type:saml2-bearer";

    /**
     * Hybrid
     */
    String HYBRID = "hybrid";
}
