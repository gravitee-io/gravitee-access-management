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
 * OpenID Connect Response Types
 *
 * See <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html">OAuth 2.0 Multiple Response Type Encoding Practices</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResponseType {
    /**
     * When supplied as the response_type parameter in an OAuth 2.0 Authorization Request, a successful response MUST include the parameter id_token.
     * The Authorization Server SHOULD NOT return an OAuth 2.0 Authorization Code, Access Token, or Access Token Type in a successful response to the grant request.
     * If a redirect_uri is supplied, the User Agent SHOULD be redirected there after granting or denying access.
     * The request MAY include a state parameter, and if so, the Authorization Server MUST echo its value as a response parameter when issuing either a successful response or an error response.
     * The default Response Mode for this Response Type is the fragment encoding and the query encoding MUST NOT be used.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     */
    String ID_TOKEN = "id_token";

    /**
     * When supplied as the response_type parameter in an OAuth 2.0 Authorization Request, the Authorization Server SHOULD NOT return an OAuth 2.0 Authorization Code, Access Token, Access Token Type, or ID Token in a successful response to the grant request.
     * If a redirect_uri is supplied, the User Agent SHOULD be redirected there after granting or denying access.
     * The request MAY include a state parameter, and if so, the Authorization Server MUST echo its value as a response parameter when issuing either a successful response or an error response.
     * The default Response Mode for this Response Type is the query encoding.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     * The Response Type none SHOULD NOT be combined with other Response Types.
     */
    String NONE = "none";

    /**
     * When supplied as the value for the response_type parameter, a successful response MUST include an Access Token, an Access Token Type, and an Authorization Code.
     * he default Response Mode for this Response Type is the fragment encoding and the query encoding MUST NOT be used.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     */
    String CODE_TOKEN = "code token";

    /**
     * When supplied as the value for the response_type parameter, a successful response MUST include both an Authorization Code and an id_token.
     * The default Response Mode for this Response Type is the fragment encoding and the query encoding MUST NOT be used.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     */
    String CODE_ID_TOKEN = "code id_token";

    /**
     * When supplied as the value for the response_type parameter, a successful response MUST include an Access Token, an Access Token Type, and an id_token.
     * The default Response Mode for this Response Type is the fragment encoding and the query encoding MUST NOT be used.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     */
    String ID_TOKEN_TOKEN = "id_token token";

    /**
     * When supplied as the value for the response_type parameter, a successful response MUST include an Authorization Code, an id_token, an Access Token, and an Access Token Type.
     * The default Response Mode for this Response Type is the fragment encoding and the query encoding MUST NOT be used.
     * Both successful and error responses SHOULD be returned using the supplied Response Mode, or if none is supplied, using the default Response Mode.
     */
    String CODE_ID_TOKEN_TOKEN = "code id_token token";
}
