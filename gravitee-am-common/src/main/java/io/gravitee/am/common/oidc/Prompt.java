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

import java.util.Arrays;
import java.util.List;

/**
 * OpenID Connect Prompt values
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest>3.1.2.1.  Authentication Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Prompt {

    /**
     * The Authorization Server MUST NOT display any authentication or consent user interface pages.
     * An error is returned if an End-User is not already authenticated or the Client does not have pre-configured consent for the requested Claims or does not fulfill other conditions for processing the request.
     * The error code will typically be login_required, interaction_required, or another code defined in Section 3.1.2.6.
     * This can be used as a method to check for existing authentication and/or consent.
     */
    String NONE = "none";

    /**
     * The Authorization Server SHOULD prompt the End-User for reauthentication.
     * If it cannot reauthenticate the End-User, it MUST return an error, typically login_required.
     */
    String LOGIN = "login";

    /**
     * The Authorization Server SHOULD prompt the End-User for consent before returning information to the Client.
     * If it cannot obtain consent, it MUST return an error, typically consent_required.
     */
    String CONSENT = "consent";

    /**
     * The Authorization Server SHOULD prompt the End-User to select a user account.
     * This enables an End-User who has multiple accounts at the Authorization Server to select amongst the multiple accounts that they might have current sessions for.
     * If it cannot obtain an account selection choice made by the End-User, it MUST return an error, typically account_selection_required.
     */
    String SELECT_ACCOUNT = "select_account";

    /**
     * An extension to the OpenID Connect Authentication Framework defining a new value for the prompt parameter
     * that instructs the OpenID Provider to start the user factor enrollment experience
     * and after the user factor has been created return the requested tokens to the client to complete the authentication flow.
     */
    String MFA_ENROLL = "mfa_enroll";

    static List<String> supportedValues(boolean filterCustomValues) {
        return filterCustomValues ? Arrays.asList(NONE, LOGIN, CONSENT) : Arrays.asList(NONE, LOGIN, CONSENT, MFA_ENROLL);
    }
}
