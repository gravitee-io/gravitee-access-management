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
 * OIDC Parameters
 *
 * See <a href="https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml">OAuth Parameters</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Parameters {

    /**
     * String value used to associate a Client session with an ID Token, and to mitigate replay attacks.
     */
    NONCE("nonce"),
    /**
     * ASCII string value that specifies how the Authorization Server displays the authentication and consent user interface pages to the End-User.
     */
    DISPLAY("display"),
    /**
     * Space delimited, case sensitive list of ASCII string values that specifies whether the Authorization Server prompts the End-User for reauthentication and consent.
     */
    PROMPT("prompt"),
    /**
     * Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time the End-User was actively authenticated by the OP.
     */
    MAX_AGE("max_age"),
    /**
     * End-User's preferred languages and scripts for the user interface, represented as a space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
     */
    UI_LOCALES("ui_locales"),
    /**
     * End-User's preferred languages and scripts for Claims being returned, represented as a space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
     */
    CLAIMS_LOCALES("claims_locales"),
    /**
     * ID Token previously issued by the Authorization Server being passed as a hint about the End-User's current or past authenticated session with the Client.
     */
    ID_TOKEN_HINT("id_token_hint"),
    /**
     * Hint to the Authorization Server about the login identifier the End-User might use to log in (if necessary).
     */
    LOGIN_HINT("login_hint"),
    /**
     * Requested Authentication Context Class Reference values.
     */
    ACR_VALUES("acr_values"),
    /**
     * This parameter is used to request that specific Claims be returned.
     */
    CLAIMS("claims"),
    /**
     * This parameter is used by the Client to provide information about itself to a Self-Issued OP that would normally be provided to an OP during Dynamic Client Registration.
     */
    REGISTRATION("registration"),
    /**
     * This parameter enables OpenID Connect requests to be passed in a single, self-contained parameter and to be optionally signed and/or encrypted.
     */
    REQUEST("request"),
    /**
     * This parameter enables OpenID Connect requests to be passed by reference, rather than by value.
     */
    REQUEST_URI("request_uri");

    private String value;

    Parameters(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
