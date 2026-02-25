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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import io.gravitee.am.model.TrustedIssuer;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a validated token with its extracted claims and metadata.
 *
 * This class is used to store the result of token validation, containing
 * all relevant information extracted from the subject token.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
@Builder
@Getter
public class ValidatedToken {

    /**
     * The subject (sub claim) from the token.
     */
    private final String subject;

    /**
     * The issuer (iss claim) from the token.
     */
    private final String issuer;

    /**
     * All claims from the token.
     */
    private final Map<String, Object> claims;

    /**
     * The scopes granted in the token.
     */
    private final Set<String> scopes;

    /**
     * The expiration time (exp claim) from the token.
     */
    private final Date expiration;

    /**
     * The issued at time (iat claim) from the token.
     */
    private final Date issuedAt;

    /**
     * The not before time (nbf claim) from the token.
     */
    private final Date notBefore;

    /**
     * The token ID (jti claim) from the token.
     */
    private final String tokenId;

    /**
     * The audience (aud claim) from the token.
     */
    private final List<String> audience;

    /**
     * The client ID that originally requested this token.
     */
    private final String clientId;

    /**
     * The token type that was validated.
     */
    private final String tokenType;

    /**
     * The domain/realm the token was issued for.
     */
    private final String domain;

    /**
     * The trusted issuer config used to validate this token when it was validated via an external issuer.
     * Null when validated with the domain certificate. Used for scope mapping and user binding (EL context and criteria).
     */
    private final TrustedIssuer trustedIssuer;

    /**
     * Whether this token was validated via a trusted external issuer (not the domain certificate).
     * Derived from {@link #trustedIssuer} for convenience.
     */
    public boolean isTrustedIssuerValidated() {
        return trustedIssuer != null;
    }

    /**
     * Get a specific claim by name.
     *
     * @param claimName the name of the claim
     * @return the claim value, or null if not present
     */
    public Object getClaim(String claimName) {
        return claims != null ? claims.get(claimName) : null;
    }

    /**
     * Get a specific claim by name with type casting.
     *
     * @param claimName the name of the claim
     * @param claimType the expected type of the claim
     * @param <T> the type parameter
     * @return the claim value, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String claimName, Class<T> claimType) {
        Object value = getClaim(claimName);
        if (value != null && claimType.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Check if a specific claim exists.
     *
     * @param claimName the name of the claim
     * @return true if the claim exists
     */
    public boolean hasClaim(String claimName) {
        return claims != null && claims.containsKey(claimName);
    }

    /**
     * Check if the token has any scopes.
     *
     * @return true if scopes are present
     */
    public boolean hasScopes() {
        return scopes != null && !scopes.isEmpty();
    }

    /**
     * Check if the token has a specific scope.
     *
     * @param scope the scope to check
     * @return true if the scope is present
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
