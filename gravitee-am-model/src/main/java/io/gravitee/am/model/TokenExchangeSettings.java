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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.gravitee.am.common.oauth2.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.ID_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.JWT;
import static io.gravitee.am.common.oauth2.TokenType.REFRESH_TOKEN;

/**
 * RFC 8693 Token Exchange Settings
 *
 * Configuration settings for OAuth 2.0 Token Exchange functionality.
 * Supports both impersonation and delegation use cases.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TokenExchangeSettings {

    private static final List<String> DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES = List.of(ACCESS_TOKEN, ID_TOKEN);

    /**
     * Minimum allowed value for maxDelegationDepth.
     */
    public static final int MIN_MAX_DELEGATION_DEPTH = 1;

    /**
     * Maximum allowed value for maxDelegationDepth.
     */
    public static final int MAX_MAX_DELEGATION_DEPTH = 100;

    /**
     * Default value for maxDelegationDepth.
     */
    public static final int DEFAULT_MAX_DELEGATION_DEPTH = 25;

    /**
     * Enable or disable token exchange functionality.
     */
    private boolean enabled = false;

    /**
     * List of allowed subject token types that can be exchanged.
     * Supports ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, and JWT.
     */
    private List<String> allowedSubjectTokenTypes;

    /**
     * List of allowed requested token types that can be issued.
     * Supports ACCESS_TOKEN and ID_TOKEN.
     */
    private List<String> allowedRequestedTokenTypes;

    /**
     * Allow impersonation scenarios where the new token represents the subject directly.
     * At least one of allowImpersonation or allowDelegation must be enabled.
     */
    private boolean allowImpersonation = true;

    /**
     * List of allowed actor token types that can be used for delegation.
     * Supports ACCESS_TOKEN, ID_TOKEN, and JWT.
     */
    private List<String> allowedActorTokenTypes;

    /**
     * Allow delegation scenarios where the actor acts on behalf of the subject.
     * When enabled, actor_token can be provided and "act" claim is added to issued token.
     * At least one of allowImpersonation or allowDelegation must be enabled.
     */
    private boolean allowDelegation = false;

    /**
     * Maximum depth of delegation chain (nested "act" claims).
     * Must be between {@link #MIN_MAX_DELEGATION_DEPTH} and {@link #MAX_MAX_DELEGATION_DEPTH}.
     * Default is {@link #DEFAULT_MAX_DELEGATION_DEPTH}.
     */
    private int maxDelegationDepth = DEFAULT_MAX_DELEGATION_DEPTH;

    /**
     * List of trusted external issuers whose JWTs can be accepted as subject/actor tokens.
     * Null means no trusted issuers (only domain-issued tokens accepted).
     */
    private List<TrustedIssuer> trustedIssuers;

    public TokenExchangeSettings() {
        this.allowedSubjectTokenTypes = new ArrayList<>(List.of(ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, JWT));
        this.allowedRequestedTokenTypes = new ArrayList<>(DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES);
        this.allowedActorTokenTypes = new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN, JWT));
    }

    /**
     * Gets allowed requested token types, returning default values if null.
     * This provides backward compatibility when loading old data that doesn't have this field.
     */
    public List<String> getAllowedRequestedTokenTypes() {
        if (allowedRequestedTokenTypes == null) {
            return new ArrayList<>(DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES);
        }
        return allowedRequestedTokenTypes;
    }

    /**
     * Set maximum delegation depth, clamped to the allowed range
     * [{@link #MIN_MAX_DELEGATION_DEPTH}, {@link #MAX_MAX_DELEGATION_DEPTH}].
     *
     * @param maxDelegationDepth the maximum delegation depth
     */
    public void setMaxDelegationDepth(int maxDelegationDepth) {
        this.maxDelegationDepth = Math.max(MIN_MAX_DELEGATION_DEPTH, Math.min(MAX_MAX_DELEGATION_DEPTH, maxDelegationDepth));
    }

    /**
     * Validate the settings consistency.
     * When token exchange is enabled, at least one of impersonation or delegation must be enabled.
     *
     * @throws IllegalStateException if the settings are invalid
     */
    public void validate() {
        if (enabled && !allowImpersonation && !allowDelegation) {
            throw new IllegalStateException("Token exchange is enabled but neither impersonation nor delegation is allowed. At least one must be enabled.");
        }
    }

    /**
     * Check if the settings are valid without throwing an exception.
     *
     * @return true if settings are valid, false otherwise
     */
    public boolean isValid() {
        if (!enabled) {
            return true;
        }
        if (!(allowImpersonation || allowDelegation)
                || allowedSubjectTokenTypes == null || allowedSubjectTokenTypes.isEmpty()
                || allowedRequestedTokenTypes == null || allowedRequestedTokenTypes.isEmpty()
                || (allowDelegation && (allowedActorTokenTypes == null || allowedActorTokenTypes.isEmpty()))
                || (allowDelegation && (maxDelegationDepth < MIN_MAX_DELEGATION_DEPTH || maxDelegationDepth > MAX_MAX_DELEGATION_DEPTH))) {
                        return false;
        }
        return areTrustedIssuersValid();
    }

    private boolean areTrustedIssuersValid() {
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            return true;
        }

        Set<String> seenIssuers = new HashSet<>();
        for (TrustedIssuer ti : trustedIssuers) {
            if (ti.getIssuer() == null || ti.getIssuer().isBlank()) {
                return false;
            }
            if (!seenIssuers.add(ti.getIssuer())) {
                return false; // duplicate issuer
            }
            String method = ti.getKeyResolutionMethod();
            if (!TrustedIssuer.KEY_RESOLUTION_JWKS_URL.equals(method)
                    && !TrustedIssuer.KEY_RESOLUTION_PEM.equals(method)) {
                return false;
            }
            if (TrustedIssuer.KEY_RESOLUTION_JWKS_URL.equals(method)) {
                if (ti.getJwksUri() == null || ti.getJwksUri().isBlank()) {
                    return false;
                }
                try {
                    URI.create(ti.getJwksUri()).toURL();
                } catch (Exception e) {
                    return false;
                }
            }
            if (TrustedIssuer.KEY_RESOLUTION_PEM.equals(method)) {
                if (ti.getCertificate() == null || ti.getCertificate().isBlank()) {
                    return false;
                }
            }
            if (!isUserBindingValid(ti)) {
                return false;
            }
        }
        return true;
    }

    private boolean isUserBindingValid(TrustedIssuer ti) {
        if (!ti.isUserBindingEnabled()) {
            return true;
        }
        var mappings = ti.getUserBindingMappings();
        if (mappings == null || mappings.isEmpty()) {
            return false;
        }
        for (var entry : mappings.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                return false;
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                return false;
            }
        }
        return true;
    }
}
