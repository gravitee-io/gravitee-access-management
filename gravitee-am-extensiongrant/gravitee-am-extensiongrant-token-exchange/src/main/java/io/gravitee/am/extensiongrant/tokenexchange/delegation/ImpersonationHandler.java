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

import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Handler for impersonation scenarios in RFC 8693 Token Exchange.
 *
 * Impersonation differs from delegation in that the acting party becomes
 * indistinguishable from the subject - there is no 'act' claim in the
 * resulting token. This is a sensitive operation requiring explicit
 * authorization.
 *
 * Impersonation use cases:
 * - Admin support accessing user account
 * - System processes acting as specific users
 * - Testing and debugging scenarios
 *
 * Security considerations:
 * - Impersonation should be disabled by default
 * - Only specifically authorized clients should be able to impersonate
 * - All impersonation events should be logged for audit
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-1.1">RFC 8693 Section 1.1 - Delegation vs Impersonation</a>
 * @author GraviteeSource Team
 */
public class ImpersonationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImpersonationHandler.class);

    /**
     * Scope required for impersonation capability.
     */
    public static final String IMPERSONATION_SCOPE = "impersonation";

    /**
     * Alternative scope for admin impersonation.
     */
    public static final String ADMIN_IMPERSONATION_SCOPE = "admin:impersonation";

    /**
     * Result of impersonation authorization check.
     */
    public static class ImpersonationResult {
        private final boolean allowed;
        private final String reason;
        private final ImpersonationType type;

        private ImpersonationResult(boolean allowed, String reason, ImpersonationType type) {
            this.allowed = allowed;
            this.reason = reason;
            this.type = type;
        }

        public static ImpersonationResult allowed(ImpersonationType type) {
            return new ImpersonationResult(true, null, type);
        }

        public static ImpersonationResult denied(String reason) {
            return new ImpersonationResult(false, reason, null);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }

        public ImpersonationType getType() {
            return type;
        }
    }

    /**
     * Type of impersonation being performed.
     */
    public enum ImpersonationType {
        /**
         * Direct impersonation where actor_token is not provided.
         * The client itself is impersonating the subject.
         */
        DIRECT,

        /**
         * Delegated impersonation where actor_token is provided
         * but the result should not contain act claim.
         */
        DELEGATED
    }

    /**
     * Check if impersonation is requested and authorized.
     *
     * Impersonation is considered when:
     * 1. Configuration allows impersonation
     * 2. No actor_token is provided (direct impersonation), OR
     * 3. Actor_token is provided but allowImpersonation flag indicates
     *    the result should not contain act claim
     *
     * @param configuration the extension grant configuration
     * @param actorToken the actor token (may be null for direct impersonation)
     * @param clientScopes the scopes of the requesting client
     * @return impersonation result
     */
    public ImpersonationResult checkImpersonation(
            TokenExchangeExtensionGrantConfiguration configuration,
            ValidatedToken actorToken,
            Set<String> clientScopes) {

        // Check if impersonation is enabled
        if (!configuration.isAllowImpersonation()) {
            LOGGER.debug("Impersonation is disabled in configuration");
            return ImpersonationResult.denied("Impersonation is not allowed");
        }

        // Determine impersonation type
        ImpersonationType type = (actorToken == null) ?
                ImpersonationType.DIRECT : ImpersonationType.DELEGATED;

        // Check if client has impersonation scope
        if (!hasImpersonationScope(clientScopes)) {
            LOGGER.debug("Client does not have impersonation scope");
            return ImpersonationResult.denied(
                    "Client is not authorized for impersonation (missing scope)");
        }

        LOGGER.info("Impersonation authorized, type: {}", type);
        return ImpersonationResult.allowed(type);
    }

    /**
     * Validate impersonation request.
     *
     * @param subjectToken the subject token being impersonated
     * @param actorToken the actor token (optional)
     * @param configuration the extension grant configuration
     * @throws InvalidGrantException if impersonation is not allowed
     */
    public void validateImpersonation(ValidatedToken subjectToken,
                                      ValidatedToken actorToken,
                                      TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {

        if (!configuration.isAllowImpersonation()) {
            throw new InvalidGrantException("Impersonation is not allowed");
        }

        // Check if subject allows impersonation via may_act
        if (subjectToken.getMayActClaim() != null) {
            MayActValidator mayActValidator = new MayActValidator();

            // For impersonation, we need to validate even without actor_token
            // by checking if the requesting client is authorized
            if (actorToken != null) {
                MayActValidator.ValidationResult result = mayActValidator.validateMayAct(
                        subjectToken, actorToken, java.util.Collections.emptyList());

                if (!result.isValid()) {
                    throw new InvalidGrantException(
                            "Impersonation denied by subject's may_act constraint: " + result.getReason());
                }
            }
        }

        LOGGER.debug("Impersonation validation passed for subject: {}", subjectToken.getSubject());
    }

    /**
     * Check if the client has any impersonation-related scope.
     *
     * @param scopes the client's scopes
     * @return true if client can impersonate
     */
    private boolean hasImpersonationScope(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }

        return scopes.contains(IMPERSONATION_SCOPE) ||
               scopes.contains(ADMIN_IMPERSONATION_SCOPE) ||
               scopes.stream().anyMatch(s -> s.startsWith("impersonate:"));
    }

    /**
     * Determine if this is a delegation or impersonation scenario.
     *
     * @param configuration the extension grant configuration
     * @param hasActorToken whether an actor_token was provided
     * @return true if this should be treated as impersonation (no act claim)
     */
    public boolean isImpersonationScenario(
            TokenExchangeExtensionGrantConfiguration configuration,
            boolean hasActorToken) {

        // If impersonation is not allowed, it's always delegation
        if (!configuration.isAllowImpersonation()) {
            return false;
        }

        // If no actor_token, could be direct impersonation
        if (!hasActorToken) {
            return true;
        }

        // If actor_token is present but delegation is disabled,
        // and impersonation is enabled, treat as impersonation
        if (!configuration.isAllowDelegation() && configuration.isAllowImpersonation()) {
            return true;
        }

        // Default: if actor_token is present, it's delegation
        return false;
    }

    /**
     * Create audit information for an impersonation event.
     *
     * @param subjectToken the impersonated subject
     * @param actorToken the impersonating actor (may be null)
     * @param clientId the client performing the impersonation
     * @param type the type of impersonation
     * @return audit information map
     */
    public java.util.Map<String, Object> createAuditInfo(
            ValidatedToken subjectToken,
            ValidatedToken actorToken,
            String clientId,
            ImpersonationType type) {

        java.util.Map<String, Object> auditInfo = new java.util.LinkedHashMap<>();

        auditInfo.put("event_type", "TOKEN_EXCHANGE_IMPERSONATION");
        auditInfo.put("impersonation_type", type.name());
        auditInfo.put("impersonated_subject", subjectToken.getSubject());
        auditInfo.put("impersonated_subject_issuer", subjectToken.getIssuer());
        auditInfo.put("client_id", clientId);
        auditInfo.put("timestamp", System.currentTimeMillis());

        if (actorToken != null) {
            auditInfo.put("actor_subject", actorToken.getSubject());
            auditInfo.put("actor_issuer", actorToken.getIssuer());
            auditInfo.put("actor_client_id", actorToken.getClientId());
        }

        return auditInfo;
    }
}
