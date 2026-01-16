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

import io.gravitee.am.common.oauth2.TokenTypeURN;

import java.util.List;
import java.util.Set;

/**
 * RFC 8693 Token Exchange Settings
 *
 * Configuration settings for OAuth 2.0 Token Exchange functionality.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 *
 * @author GraviteeSource Team
 */
public class TokenExchangeSettings {

    /**
     * Enable or disable token exchange functionality.
     */
    private boolean enabled = false;

    /**
     * List of allowed subject token types that can be exchanged.
     * Defaults to ACCESS_TOKEN, ID_TOKEN, and JWT.
     */
    private List<String> allowedSubjectTokenTypes = TokenTypeURN.DEFAULT_SUBJECT_TOKEN_TYPES;

    /**
     * List of allowed actor token types for delegation scenarios.
     * Defaults to ACCESS_TOKEN and JWT.
     */
    private List<String> allowedActorTokenTypes = TokenTypeURN.DEFAULT_ACTOR_TOKEN_TYPES;

    /**
     * List of token types that can be requested as output.
     * Defaults to ACCESS_TOKEN and JWT.
     */
    private List<String> allowedRequestedTokenTypes = TokenTypeURN.DEFAULT_REQUESTED_TOKEN_TYPES;

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
    private Set<String> trustedIssuers;

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
     * REDUCE: Only allow subset of original scopes (default, most secure)
     * PRESERVE: Keep original scopes unchanged
     * CUSTOM: Allow any requested scopes (least restrictive)
     */
    private ScopePolicy scopePolicy = ScopePolicy.REDUCE;

    /**
     * Default token lifetime in seconds for exchanged tokens.
     * If not set, uses the domain's default access token lifetime.
     */
    private Integer defaultTokenLifetime;

    /**
     * Whether to issue refresh tokens during token exchange.
     */
    private boolean issueRefreshToken = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedSubjectTokenTypes() {
        return allowedSubjectTokenTypes;
    }

    public void setAllowedSubjectTokenTypes(List<String> allowedSubjectTokenTypes) {
        this.allowedSubjectTokenTypes = allowedSubjectTokenTypes;
    }

    public List<String> getAllowedActorTokenTypes() {
        return allowedActorTokenTypes;
    }

    public void setAllowedActorTokenTypes(List<String> allowedActorTokenTypes) {
        this.allowedActorTokenTypes = allowedActorTokenTypes;
    }

    public List<String> getAllowedRequestedTokenTypes() {
        return allowedRequestedTokenTypes;
    }

    public void setAllowedRequestedTokenTypes(List<String> allowedRequestedTokenTypes) {
        this.allowedRequestedTokenTypes = allowedRequestedTokenTypes;
    }

    public boolean isAllowImpersonation() {
        return allowImpersonation;
    }

    public void setAllowImpersonation(boolean allowImpersonation) {
        this.allowImpersonation = allowImpersonation;
    }

    public boolean isAllowDelegation() {
        return allowDelegation;
    }

    public void setAllowDelegation(boolean allowDelegation) {
        this.allowDelegation = allowDelegation;
    }

    public int getMaxDelegationChainDepth() {
        return maxDelegationChainDepth;
    }

    public void setMaxDelegationChainDepth(int maxDelegationChainDepth) {
        this.maxDelegationChainDepth = maxDelegationChainDepth;
    }

    public Set<String> getTrustedIssuers() {
        return trustedIssuers;
    }

    public void setTrustedIssuers(Set<String> trustedIssuers) {
        this.trustedIssuers = trustedIssuers;
    }

    public boolean isValidateSignature() {
        return validateSignature;
    }

    public void setValidateSignature(boolean validateSignature) {
        this.validateSignature = validateSignature;
    }

    public boolean isRequireAudience() {
        return requireAudience;
    }

    public void setRequireAudience(boolean requireAudience) {
        this.requireAudience = requireAudience;
    }

    public ScopePolicy getScopePolicy() {
        return scopePolicy;
    }

    public void setScopePolicy(ScopePolicy scopePolicy) {
        this.scopePolicy = scopePolicy;
    }

    public Integer getDefaultTokenLifetime() {
        return defaultTokenLifetime;
    }

    public void setDefaultTokenLifetime(Integer defaultTokenLifetime) {
        this.defaultTokenLifetime = defaultTokenLifetime;
    }

    public boolean isIssueRefreshToken() {
        return issueRefreshToken;
    }

    public void setIssueRefreshToken(boolean issueRefreshToken) {
        this.issueRefreshToken = issueRefreshToken;
    }

    @Override
    public String toString() {
        return "TokenExchangeSettings{" +
                "enabled=" + enabled +
                ", allowedSubjectTokenTypes=" + allowedSubjectTokenTypes +
                ", allowedActorTokenTypes=" + allowedActorTokenTypes +
                ", allowedRequestedTokenTypes=" + allowedRequestedTokenTypes +
                ", allowImpersonation=" + allowImpersonation +
                ", allowDelegation=" + allowDelegation +
                ", maxDelegationChainDepth=" + maxDelegationChainDepth +
                ", trustedIssuers=" + trustedIssuers +
                ", validateSignature=" + validateSignature +
                ", requireAudience=" + requireAudience +
                ", scopePolicy=" + scopePolicy +
                ", defaultTokenLifetime=" + defaultTokenLifetime +
                ", issueRefreshToken=" + issueRefreshToken +
                '}';
    }

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
}
