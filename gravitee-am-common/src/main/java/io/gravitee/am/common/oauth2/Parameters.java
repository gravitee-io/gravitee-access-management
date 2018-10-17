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
 * OAuth 2.0 Parameters
 *
 * See <a href="https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml">OAuth Parameters</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Parameters {

    /**
     * The client identifier issued to the client during the registration process.
     */
    CLIENT_ID("client_id"),
    /**
     * The client secret.
     */
    CLIENT_SECRET("client_secret"),
    /**
     * The client informs the authorization server of the desired grant type using the response_type parameter.
     */
    RESPONSE_TYPE("response_type"),
    /**
     * The authorization server redirects the user-agent to the client's redirection endpoint previously established with the
     *  authorization server during the client registration process or when making the authorization request.
     */
    REDIRECT_URI("redirect_uri"),
    /**
     * The authorization and token endpoints allow the client to specify the scope of the access request using the "scope" request parameter.
     */
    SCOPE("scope"),
    /**
     * An opaque value used by the client to maintain state between the request and callback.
     * The parameter SHOULD be used for preventing cross-site request forgery.
     */
    STATE("state"),
    /**
     * The authorization code received from the authorization server.
     */
    CODE("code"),
    /**
     * Authorization Grant
     */
    GRANT_TYPE("grant_type"),
    /**
     * The resource owner username.
     */
    USERNAME("username"),
    /**
     * The resource owner password.
     */
    PASSWORD("password"),
    /**
     * The refresh token issued to the client.
     */
    REFRESH_TOKEN("refresh_token"),
    /**
     * The assertion being used as an authorization grant.
     */
    ASSERTION("assertion"),
    /**
     * The assertion being used to authenticate the client.
     */
    CLIENT_ASSERTION("client_assertion"),
    /**
     * The format of the assertion as defined by the authorization server.
     * The value will be an absolute URI.
     */
    CLIENT_ASSERTION_TYPE("client_assertion_type"),
    /**
     * PKCE code verifier.
     */
    CODE_VERIFIER("code_verifier"),
    /**
     * PKCE code challenge.
     */
    CODE_CHALLENGE("code_challenge"),
    /**
     * PKCE code challenge method.
     */
    CODE_CHALLENGE_METHOD("code_challenge_method"),
    /**
     * UMA claim token.
     */
    CLAIM_TOKEN("claim_token"),
    /**
     * UMA PCT.
     */
    PCT("pct"),
    /**
     * UMA RPT.
     */
    RPT("rpt"),
    /**
     * UMA ticket.
     */
    TICKET("ticket"),
    /**
     * Vector of trust.
     */
    VTR("vtr");

    private String value;

    Parameters(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
