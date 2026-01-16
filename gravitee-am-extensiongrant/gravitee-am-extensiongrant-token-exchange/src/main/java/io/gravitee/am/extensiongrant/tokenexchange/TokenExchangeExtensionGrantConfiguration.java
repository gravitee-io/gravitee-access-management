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
package io.gravitee.am.extensiongrant.tokenexchange;

import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.ExtensionGrantConfiguration;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for RFC 8693 OAuth 2.0 Token Exchange Extension Grant.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TokenExchangeExtensionGrantConfiguration implements ExtensionGrantConfiguration {

    /**
     * List of allowed subject token types that can be exchanged.
     * Defaults to ACCESS_TOKEN, ID_TOKEN, and JWT.
     */
    private List<String> allowedSubjectTokenTypes = new ArrayList<>(TokenTypeURN.DEFAULT_SUBJECT_TOKEN_TYPES);

    /**
     * List of allowed actor token types for delegation scenarios.
     * Defaults to ACCESS_TOKEN and JWT.
     */
    private List<String> allowedActorTokenTypes = new ArrayList<>(TokenTypeURN.DEFAULT_ACTOR_TOKEN_TYPES);

    /**
     * List of token types that can be requested as output.
     * Defaults to ACCESS_TOKEN and JWT.
     */
    private List<String> allowedRequestedTokenTypes = new ArrayList<>(TokenTypeURN.DEFAULT_REQUESTED_TOKEN_TYPES);

    /**
     * Allow impersonation scenarios where the acting party becomes indistinguishable
     * from the subject. This is a sensitive operation and should be used with caution.
     */
    private boolean allowImpersonation = false;

    /**
     * Allow delegation scenarios where the actor acts on behalf of the subject
     * while maintaining separate identities (using the 'act' claim).
     */
    private boolean allowDelegation = true;

    /**
     * Maximum depth of the delegation chain (nested 'act' claims).
     * Prevents excessive delegation chains for security.
     */
    private int maxDelegationChainDepth = 3;

    /**
     * List of trusted token issuers. If empty, tokens from any issuer are accepted
     * (subject to signature validation). If specified, only tokens from these issuers
     * will be accepted.
     */
    private Set<String> trustedIssuers = new HashSet<>();

    /**
     * Whether to validate the cryptographic signature of input tokens.
     * Highly recommended to keep enabled for security.
     */
    private boolean validateSignature = true;

    /**
     * Whether the 'audience' parameter is required in token exchange requests.
     */
    private boolean requireAudience = false;

    /**
     * Scope handling policy during token exchange.
     */
    private ScopePolicy scopePolicy = ScopePolicy.REDUCE;

    /**
     * Public key or JWKS URL for validating external JWT tokens.
     * Used when validating tokens from external issuers.
     */
    private String publicKeyResolver;

    /**
     * The public key (PEM format) or JWKS URL for token validation.
     */
    private String publicKey;

    /**
     * Whether to issue refresh tokens during token exchange.
     */
    private boolean issueRefreshToken = false;

    /**
     * Claims mapper to map claims from the subject token to the issued token.
     */
    private List<ClaimMapping> claimsMapper;

    /**
     * Scope handling policy for token exchange.
     */
    public enum ScopePolicy {
        /**
         * Only allow a subset of the original token's scopes (most secure).
         * Requested scopes must be a subset of the subject token's scopes.
         */
        REDUCE,

        /**
         * Preserve the original token's scopes unchanged.
         * The scope parameter in the request is ignored.
         */
        PRESERVE,

        /**
         * Allow any requested scopes (least restrictive).
         * Use with caution as this may allow scope escalation.
         */
        CUSTOM
    }

    /**
     * Key resolver type for external token validation.
     */
    public enum KeyResolver {
        /**
         * Use a provided public key in PEM format.
         */
        GIVEN_KEY,

        /**
         * Retrieve public keys from a JWKS URL.
         */
        JWKS_URL
    }

    /**
     * Mapping configuration for claims from subject token to issued token.
     */
    @Getter
    @Setter
    public static class ClaimMapping {
        /**
         * The claim name in the subject token.
         */
        private String sourceClaim;

        /**
         * The claim name in the issued token.
         */
        private String targetClaim;
    }
}
