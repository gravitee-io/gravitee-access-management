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
    String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /**
     * Device code flow
     * See <a href="https://tools.ietf.org/html/draft-ietf-oauth-device-flow-15#section-3.4">3.4 Device Access Token Request</a>
     */
    String DEVIDE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    /**
     * SAML 2.0 Bearer
     *
     * See <a href="https://tools.ietf.org/html/rfc7522#section-2.1">2.1. Using SAML Assertions as Authorization Grants</a>
     */
    String SAML2_BEARER = "urn:ietf:params:oauth:grant-type:saml2-bearer";

    /**
     * Hybrid
     */
    String HYBRID = "hybrid";

    /**
     * User Managed Access 2.0 grant.
     * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">3.3.1 Client Request to Authorization Server for RPT</a>
     */
    String UMA = "urn:ietf:params:oauth:grant-type:uma-ticket";

    /**
     * CIBA grant type
     *
     * See <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.4"> 4. Registration and Discovery Metadata </a>
     */
    String CIBA_GRANT_TYPE = "urn:openid:params:grant-type:ciba";

    /**
     * Token Exchange grant type
     *
     * See <a href="https://tools.ietf.org/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
     */
    String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
}
