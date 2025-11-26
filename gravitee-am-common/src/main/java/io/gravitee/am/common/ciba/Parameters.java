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
package io.gravitee.am.common.ciba;

/**
 * See <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.7.1">openid-client-initiated-backchannel-authentication-core</a>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Parameters {
    /**
     *  It is a bearer token provided by the Client that will be used by the OpenID Provider to authenticate the callback
     *  request to the Client
     */
    String CLIENT_NOTIFICATION_TOKEN = "client_notification_token";
    /**
     * A token containing information identifying the end-user for whom authentication is being requested.
     */
    String LOGIN_HINT_TOKEN = "login_hint_token";
    /**
     * A  human-readable identifier or message intended to be displayed on both the consumption device and
     * the authentication device to interlock them together for the transaction by way of a visual cue for the end-user.
     */
    String BINDING_MESSAGE = "binding_message";
    /**
     * A secret code, such as a password or pin, that is known only to the user but verifiable by the OP.
     */
    String USER_CODE = "user_code";
    /**
     * A positive integer allowing the client to request the expires_in value for the auth_req_id the server will return
     */
    String REQUESTED_EXPIRY = "requested_expiry";
    /**
     * The time when the authentication request expires, measured in the number of seconds since the UNIX epoch.
     */
    String EXPIRY = "exp";

    String NBF = "nbf";

    /**
     * The unique identifier to identify the authentication request (transaction) made by the Client.
     */
    String AUTH_REQ_ID = "auth_req_id";
}
