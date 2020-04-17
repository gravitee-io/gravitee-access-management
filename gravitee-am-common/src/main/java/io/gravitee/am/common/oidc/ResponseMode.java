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
 * OIDC JARM Response Modes
 *
 * See <a href="https://openid.net//specs/openid-financial-api-jarm.html">JWT Secured Authorization Response Mode for OAuth 2.0 (JARM)</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResponseMode {

    /**
     * The response mode "query.jwt" causes the authorization server to send the authorization response as HTTP
     * redirect to the redirect URI of the client. The authorization server adds the parameter response containing the
     * JWT as defined in section 4.1. to the query component of the redirect URI using the
     * "application/x-www-form-urlencoded" format.
     */
    String QUERY_JWT = "query.jwt";

    /**
     * The response mode "fragment.jwt" causes the authorization server to send the authorization response as HTTP
     * redirect to the redirect URI of the client. The authorization server adds the parameter response containing the
     * JWT as defined in section 4.1. to the fragment component of the redirect URI using the
     * "application/x-www-form-urlencoded" format.
     */
    String FRAGMENT_JWT = "fragment.jwt";

    /**
     * The response mode "form_post.jwt" uses the technique described in [OIFP] to convey the JWT to the client. The
     * response parameter containing the JWT is encoded as HTML form value that is auto-submitted in the User Agent,
     * and thus is transmitted via the HTTP POST method to the Client, with the result parameters being encoded in the
     * body using the "application/x-www-form-urlencoded" format.
     */
    String FORM_POST_JWT = "form_post.jwt";

    /**
     * The response mode "jwt" is a shortcut and indicates the default redirect encoding (query, fragment) for the
     * requested response type. The default for response type "code" is "query.jwt" whereas the default for "token" and
     * the response types defined in [OIDM], except "none", is "fragment.jwt".
     */
    String JWT = "jwt";
}
