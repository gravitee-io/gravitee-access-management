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
package io.gravitee.am.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Represents a trusted external issuer for RFC 8693 Token Exchange.
 *
 * When configured, external JWTs from this issuer can be accepted as subject or actor tokens
 * during token exchange. The JWT signature is validated against the issuer's key material
 * (JWKS URL or PEM certificate).
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TrustedIssuer {

    public static final String KEY_RESOLUTION_JWKS_URL = "JWKS_URL";
    public static final String KEY_RESOLUTION_PEM = "PEM";

    /**
     * The expected "iss" claim value in the external JWT.
     */
    private String issuer;

    /**
     * Key resolution method: "JWKS_URL" or "PEM".
     */
    private String keyResolutionMethod;

    /**
     * JWKS endpoint URL. Required when keyResolutionMethod is "JWKS_URL".
     */
    private String jwksUri;

    /**
     * PEM-encoded X.509 certificate. Required when keyResolutionMethod is "PEM".
     */
    private String certificate;

    /**
     * External scope to domain scope mappings.
     * Key = external scope from the JWT, Value = domain scope to map to.
     * Unmapped scopes are dropped (fail-closed).
     * Null or empty means pass through all scopes unchanged.
     */
    private Map<String, String> scopeMappings;

    /**
     * Whether to look up a domain user matching the external JWT claims.
     * When enabled, the minted token will carry the domain user's roles, groups, and profile data.
     * When disabled (default), a synthetic user is built from token claims.
     */
    private boolean userBindingEnabled;

    /**
     * Mappings for user binding lookup.
     * Key = user attribute to search by (e.g., "email", "username").
     * Value = claim name or EL expression (e.g., "email" or "{#token['email']}").
     * Required when userBindingEnabled is true.
     */
    private Map<String, String> userBindingMappings;
}
