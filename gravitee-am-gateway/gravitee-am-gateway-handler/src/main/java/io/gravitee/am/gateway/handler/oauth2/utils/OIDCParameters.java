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
 * See <a href="https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml">OAuth 2.0 parameters</a>
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.  Authentication Request</a>
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">9 Client Authentication</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface OIDCParameters {

    /**
     * OPTIONAL. Space delimited, case sensitive list of ASCII string values that specifies whether the Authorization Server prompts the End-User for reauthentication and consent.
     */
    String PROMPT = "prompt";
    /**
     * OPTIONAL. String value used to associate a Client session with an ID Token, and to mitigate replay attacks.
     */
    String NONCE = "nonce";
    /**
     * OPTIONAL. Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time the End-User was actively authenticated by the OP.
     * If the elapsed time is greater than this value, the OP MUST attempt to actively re-authenticate the End-User.
     */
    String MAX_AGE = "max_age";

    /**
     * OPTIONAL. This parameter is used to request that specific Claims be returned. The value is a JSON object listing the requested Claims.
     */
    String CLAIMS = "claims";

    /**
     *
     */
    String CLIENT_ASSERTION_TYPE = "client_assertion_type";

    /**
     *
     */
    String CLIENT_ASSERTION = "client_assertion";
}
