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
package io.gravitee.am.common.oidc;

/**
 * OpenID Connect Client Authentication methods
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">9. Client Authentication/a>
 * See <a href="https://tools.ietf.org/html/rfc8705#section-2">2.  Mutual TLS for OAuth Client Authentication</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientAuthenticationMethod {

    /**
     * Clients that have received a client_secret value from the Authorization Server authenticate with the Authorization Server
     * in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] using the HTTP Basic authentication scheme.
     */
    String CLIENT_SECRET_BASIC = "client_secret_basic";

    /**
     * Clients that have received a client_secret value from the Authorization Server, authenticate with the Authorization Server
     * in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] by including the Client Credentials in the request body.
     */
    String CLIENT_SECRET_POST = "client_secret_post";

    /**
     * Clients that have received a client_secret value from the Authorization Server create a JWT using an HMAC SHA algorithm, such as HMAC SHA-256.
     * The HMAC (Hash-based Message Authentication Code) is calculated using the octets of the UTF-8 representation of the client_secret as the shared key.
     */
    String CLIENT_SECRET_JWT = "client_secret_jwt";

    /**
     * Clients that have registered a public key sign a JWT using that key.
     */
    String PRIVATE_KEY_JWT = "private_key_jwt";

    /**
     * The Client does not authenticate itself at the Token Endpoint, either because it uses only the Implicit Flow (and so does not use the Token Endpoint) or because it is a Public Client with no Client Secret or other authentication mechanism.
     */
    String NONE = "none";

    /**
     * Utilizing a PKI client certificate used in a TLS connection.
     */
    String TLS_CLIENT_AUTH = "tls_client_auth";

    /**
     * Utilizing a self-signed client certificate used in a TLS connection.
     */
    String SELF_SIGNED_TLS_CLIENT_AUTH = "self_signed_tls_client_auth";
}
