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
package io.gravitee.am.common.jwt;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * JWT Registered Claim Names
 *
 * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1">4.1. Registered Claim Names</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Claims {

    /**
     * The "iss" (issuer) claim identifies the principal that issued the JWT.
     */
    String ISS = "iss";

    /**
     * The "sub" (subject) claim identifies the principal that is the subject of the JWT.
     */
    String SUB = "sub";
    /**
     * The gravitee "internal" subject claim identifies the principal that is the subject of the JWT using AM internal data.
     * This claim is used by AM instead of the sub claim to ensure the uniqueness of the user profile identified by a token.
     * Only used by Domain in v2 or higher
     */
    String GIO_INTERNAL_SUB = "gis";

    /**
     * The "aud" (audience) claim identifies the recipients that the JWT is intended for.
     */
    String AUD = "aud";

    /**
     * The "exp" (expiration time) claim identifies the expiration time on or after which the JWT MUST NOT be accepted for processing.
     */
    String EXP = "exp";

    /**
     * The "nbf" (not before) claim identifies the time before which the JWT MUST NOT be accepted for processing.
     */
    String NBF = "nbf";

    /**
     * The "iat" (issued at) claim identifies the time at which the JWT was issued.
     */
    String IAT = "iat";

    /**
     * The "jti" (JWT ID) claim provides a unique identifier for the JWT.
     */
    String JTI = "jti";

    /**
     * The "domain" (domain) claim identifies the domain that the JWT is intended for.
     */
    String DOMAIN = "domain";

    /**
     * The "org" (organization) claim identifies the organization that the JWT is intended for.
     */
    String ORGANIZATION = "org";

    /**
     * The "env" (environment) claim identifies the environment that the JWT is intended for.
     */
    String ENVIRONMENT = "env";

    /**
     * The claims parameter used to request that specific Claims be returned
     */
    String CLAIMS = "claims_request_parameter";

    /**
     * The "ip_address" (IP Address) claim identifies the remote client ip used for the JWT.
     */
    String IP_ADDRESS = "ip_address";

    /**
     * The "user_agent" (User Agent) claim identifies the user agent used for the JWT.
     */
    String USER_AGENT = "user_agent";

    /**
     * The oauth 2.0 "scopes"
     */
    String SCOPE = "scope";
    /**
     * The oauth 2.0 confirmation method
     */
    String CNF = "cnf";

    /**
     * Time when the End-User authentication occurred.
     */
    String AUTH_TIME = "auth_time";

    /**
     * Time the End-User's information was last updated.
     */
    String UPDATED_AT = "updated_at";
    /**
     * Encrypted code verifier for PKCE with social IDP
     */
    String ENCRYPTED_CODE_VERIFIER = "ecv";

    /**
     * Encrypted code verifier for PKCE with social IDP
     */
    String ECV = "ecv";

    /**
     * RFC 8707 Resource Indicators - Original resources claim in refresh tokens
     * Stores the resources originally granted during authorization for consistency validation
     */
    String ORIG_RESOURCES = "orig_resources";

    String CLIENT_ID = "client_id";

    /**
     * RFC 8693 Token Exchange - Actor claim for delegation scenarios.
     * Contains a JSON object with claims identifying the actor (at minimum "sub").
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1</a>
     */
    String ACT = "act";

    static List<String> getAllClaims() {
        return Arrays.asList(ISS, SUB, AUD, EXP, NBF, IAT, AUTH_TIME, UPDATED_AT,
                JTI, DOMAIN, CLAIMS, IP_ADDRESS, USER_AGENT, SCOPE, CNF, CLIENT_ID);
    }

    static Set<String> requireEncryption() {
        return Set.of(ENCRYPTED_CODE_VERIFIER);
    }

}
