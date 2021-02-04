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
    String iss = "iss";

    /**
     * The "sub" (subject) claim identifies the principal that is the subject of the JWT.
     */
    String sub = "sub";

    /**
     * The "aud" (audience) claim identifies the recipients that the JWT is intended for.
     */
    String aud = "aud";

    /**
     * The "exp" (expiration time) claim identifies the expiration time on or after which the JWT MUST NOT be accepted for processing.
     */
    String exp = "exp";

    /**
     * The "nbf" (not before) claim identifies the time before which the JWT MUST NOT be accepted for processing.
     */
    String nbf = "nbf";

    /**
     * The "iat" (issued at) claim identifies the time at which the JWT was issued.
     */
    String iat = "iat";

    /**
     * The "jti" (JWT ID) claim provides a unique identifier for the JWT.
     */
    String jti = "jti";

    /**
     * The "domain" (domain) claim identifies the domain that the JWT is intended for.
     */
    String domain = "domain";

    /**
     * The "org" (organization) claim identifies the organization that the JWT is intended for.
     */
    String organization = "org";

    /**
     * The "env" (environment) claim identifies the environment that the JWT is intended for.
     */
    String environment = "env";

    /**
     * The claims parameter used to request that specific Claims be returned
     */
    String claims = "claims_request_parameter";

    /**
     * The "ip_address" (IP Address) claim identifies the remote client ip used for the JWT.
     */
    String ip_address = "ip_address";

    /**
     * The "user_agent" (User Agent) claim identifies the user agent used for the JWT.
     */
    String user_agent = "user_agent";

    /**
     * The oauth 2.0 "scopes"
     */
    String scope = "scope";

    static List<String> claims() {
        return Arrays.asList(iss, sub, aud, exp, nbf, iat,
                jti, domain, claims, ip_address, user_agent, scope);
    }

}
