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

import java.util.Arrays;
import java.util.Collection;

/**
 * OAuth 2.0 Parameters
 *
 * See <a href="https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml">OAuth Parameters</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Parameters {

    /**
     * The client identifier issued to the client during the registration process.
     */
    String CLIENT_ID = "client_id";
    /**
     * The client secret.
     */
    String CLIENT_SECRET = "client_secret";
    /**
     * The client informs the authorization server of the desired grant type using the response_type parameter.
     */
    String RESPONSE_TYPE = "response_type";

    /**
     * The client informs the authorization server of the mechanism to be used for returning Authorization Response
     * parameters from the Authorization_endpoint.
     */
    String RESPONSE_MODE = "response_mode";
    /**
     * The authorization server redirects the user-agent to the client's redirection endpoint previously established with the
     *  authorization server during the client registration process or when making the authorization request.
     */
    String REDIRECT_URI = "redirect_uri";
    /**
     * The authorization and token endpoints allow the client to specify the scope of the access request using the "scope" request parameter.
     */
   String SCOPE = "scope";
    /**
     * An opaque value used by the client to maintain state between the request and callback.
     * The parameter SHOULD be used for preventing cross-site request forgery.
     */
    String STATE = "state";
    /**
     * The authorization code received from the authorization server.
     */
    String CODE = "code";
    /**
     * Authorization Grant
     */
    String GRANT_TYPE = "grant_type";
    /**
     * The resource owner username.
     */
    String USERNAME = "username";
    /**
     * The resource owner password.
     */
    String PASSWORD = "password";
    /**
     * The refresh token issued to the client.
     */
    String REFRESH_TOKEN = "refresh_token";
    /**
     * The assertion being used as an authorization grant.
     */
    String ASSERTION = "assertion";
    /**
     * The assertion being used to authenticate the client.
     */
    String CLIENT_ASSERTION = "client_assertion";
    /**
     * The format of the assertion as defined by the authorization server.
     * The value will be an absolute URI.
     */
    String CLIENT_ASSERTION_TYPE = "client_assertion_type";
    /**
     * PKCE code verifier.
     */
    String CODE_VERIFIER = "code_verifier";
    /**
     * PKCE code challenge.
     */
    String CODE_CHALLENGE = "code_challenge";
    /**
     * PKCE code challenge method.
     */
    String CODE_CHALLENGE_METHOD = "code_challenge_method";
    /**
     * UMA claim token.
     */
    String CLAIM_TOKEN = "claim_token";
    /**
     * UMA claim token format.
     */
    String CLAIM_TOKEN_FORMAT = "claim_token_format";
    /**
     * UMA PCT (Persisted Claims Token).
     */
    String PCT = "pct";
    /**
     * UMA RPT (Requesting Party Token).
     */
    String RPT = "rpt";
    /**
     * UMA ticket.
     */
    String TICKET = "ticket";
    /**
     * Vector of trust.
     */
    String VTR = "vtr";

    String RESOURCE = "resource";

    /**
     * Token Exchange: The security token representing the identity of the party on behalf of whom the request is being made.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
     */
    String SUBJECT_TOKEN = "subject_token";

    /**
     * Token Exchange: An identifier for the type of the security token in the "subject_token" parameter.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
     */
    String SUBJECT_TOKEN_TYPE = "subject_token_type";

    /**
     * Token Exchange: An identifier for the type of the requested security token.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
     */
    String REQUESTED_TOKEN_TYPE = "requested_token_type";

    /**
     * Token Exchange: A security token representing the identity of the acting party.
     * Used for delegation scenarios where an actor acts on behalf of the subject.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
     */
    String ACTOR_TOKEN = "actor_token";

    /**
     * Token Exchange: An identifier for the type of the security token in the "actor_token" parameter.
     * Required when actor_token is provided.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
     */
    String ACTOR_TOKEN_TYPE = "actor_token_type";

    /**
     * Session identifier
     */
    String SESSION_ID = "session_id";

    Collection<String> values = Arrays.asList(CLIENT_ID, CLIENT_SECRET, RESPONSE_TYPE, RESPONSE_MODE, REDIRECT_URI, SCOPE, STATE, CODE, GRANT_TYPE, USERNAME, PASSWORD,
                REFRESH_TOKEN, ASSERTION, CLIENT_ASSERTION, CLIENT_ASSERTION_TYPE, CODE_VERIFIER, CODE_CHALLENGE, CODE_CHALLENGE_METHOD,
                CLAIM_TOKEN, CLAIM_TOKEN_FORMAT, PCT, RPT, TICKET, VTR, RESOURCE, SUBJECT_TOKEN, SUBJECT_TOKEN_TYPE, REQUESTED_TOKEN_TYPE,
                ACTOR_TOKEN, ACTOR_TOKEN_TYPE, SESSION_ID);
}
