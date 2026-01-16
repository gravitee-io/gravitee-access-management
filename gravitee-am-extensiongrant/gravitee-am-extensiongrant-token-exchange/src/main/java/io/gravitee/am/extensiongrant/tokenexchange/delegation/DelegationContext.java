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
package io.gravitee.am.extensiongrant.tokenexchange.delegation;

import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * Context object containing all information about a delegation or impersonation scenario.
 *
 * This class encapsulates the state of a token exchange request including:
 * - The validated subject and actor tokens
 * - Whether this is a delegation or impersonation
 * - The computed actor claim (for delegation)
 * - Granted scopes
 * - Audit information
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
@Builder
@Getter
public class DelegationContext {

    /**
     * The validated subject token.
     */
    private final ValidatedToken subjectToken;

    /**
     * The validated actor token (null if no actor_token was provided).
     */
    private final ValidatedToken actorToken;

    /**
     * Whether this is an impersonation (true) or delegation (false) scenario.
     */
    private final boolean impersonation;

    /**
     * The delegation type.
     */
    private final DelegationType delegationType;

    /**
     * The computed actor claim for the issued token (null for impersonation).
     */
    private final Map<String, Object> actorClaim;

    /**
     * The scopes granted to the issued token.
     */
    private final Set<String> grantedScopes;

    /**
     * The depth of the delegation chain.
     */
    private final int delegationChainDepth;

    /**
     * The target audience for the issued token.
     */
    private final String audience;

    /**
     * The target resource for the issued token.
     */
    private final String resource;

    /**
     * The requested token type.
     */
    private final String requestedTokenType;

    /**
     * The client ID performing the token exchange.
     */
    private final String clientId;

    /**
     * Audit information about this delegation/impersonation.
     */
    private final Map<String, Object> auditInfo;

    /**
     * Type of delegation being performed.
     */
    public enum DelegationType {
        /**
         * Standard delegation - actor acts on behalf of subject.
         * The issued token contains an 'act' claim.
         */
        DELEGATION,

        /**
         * Direct impersonation - client impersonates subject directly.
         * No actor_token provided, no 'act' claim in issued token.
         */
        DIRECT_IMPERSONATION,

        /**
         * Delegated impersonation - actor impersonates subject.
         * Actor_token provided but no 'act' claim in issued token.
         */
        DELEGATED_IMPERSONATION,

        /**
         * No delegation or impersonation - simple token exchange.
         * Subject token exchanged without actor involvement.
         */
        NONE
    }

    /**
     * Check if this context represents a delegation scenario.
     *
     * @return true if delegation (with act claim)
     */
    public boolean isDelegation() {
        return delegationType == DelegationType.DELEGATION;
    }

    /**
     * Check if this context represents any form of impersonation.
     *
     * @return true if impersonation (no act claim)
     */
    public boolean isImpersonation() {
        return delegationType == DelegationType.DIRECT_IMPERSONATION ||
               delegationType == DelegationType.DELEGATED_IMPERSONATION;
    }

    /**
     * Check if an actor token was provided.
     *
     * @return true if actor token is present
     */
    public boolean hasActorToken() {
        return actorToken != null;
    }

    /**
     * Get the effective subject (the identity in the issued token).
     *
     * @return the subject identifier
     */
    public String getEffectiveSubject() {
        return subjectToken.getSubject();
    }

    /**
     * Get the actor subject if present.
     *
     * @return the actor subject or null
     */
    public String getActorSubject() {
        return actorToken != null ? actorToken.getSubject() : null;
    }

    /**
     * Builder helper for creating a delegation context.
     */
    public static class DelegationContextBuilder {

        /**
         * Configure as a delegation scenario.
         */
        public DelegationContextBuilder asDelegation(Map<String, Object> actorClaim, int chainDepth) {
            this.delegationType = DelegationType.DELEGATION;
            this.impersonation = false;
            this.actorClaim = actorClaim;
            this.delegationChainDepth = chainDepth;
            return this;
        }

        /**
         * Configure as a direct impersonation scenario.
         */
        public DelegationContextBuilder asDirectImpersonation() {
            this.delegationType = DelegationType.DIRECT_IMPERSONATION;
            this.impersonation = true;
            this.actorClaim = null;
            this.delegationChainDepth = 0;
            return this;
        }

        /**
         * Configure as a delegated impersonation scenario.
         */
        public DelegationContextBuilder asDelegatedImpersonation() {
            this.delegationType = DelegationType.DELEGATED_IMPERSONATION;
            this.impersonation = true;
            this.actorClaim = null;
            this.delegationChainDepth = 0;
            return this;
        }

        /**
         * Configure as no delegation (simple exchange).
         */
        public DelegationContextBuilder asSimpleExchange() {
            this.delegationType = DelegationType.NONE;
            this.impersonation = false;
            this.actorClaim = null;
            this.delegationChainDepth = 0;
            return this;
        }
    }
}
