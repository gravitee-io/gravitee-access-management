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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Validator for the 'may_act' claim as defined in RFC 8693 Section 4.4.
 *
 * The 'may_act' claim makes a statement that one party is authorized to become
 * the actor and act on behalf of another party. It is an optional claim that
 * when present, provides authorization constraints for delegation.
 *
 * Example of may_act claim:
 * <pre>
 * {
 *   "aud": "https://consumer.example.com",
 *   "iss": "https://issuer.example.com",
 *   "exp": 1443904177,
 *   "sub": "user@example.com",
 *   "may_act": {
 *     "sub": "admin@example.com",
 *     "aud": ["https://api.example.com"]
 *   }
 * }
 * </pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.4">RFC 8693 Section 4.4 - "may_act" Claim</a>
 * @author GraviteeSource Team
 */
public class MayActValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MayActValidator.class);

    /**
     * Validation result for may_act claim.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Validate whether the actor is authorized to act on behalf of the subject
     * based on the 'may_act' claim in the subject token.
     *
     * @param subjectToken the subject token containing the may_act claim
     * @param actorToken the actor token
     * @param targetAudience the target audience (optional)
     * @return validation result
     */
    public ValidationResult validateMayAct(ValidatedToken subjectToken,
                                           ValidatedToken actorToken,
                                           String targetAudience) {
        Object mayActClaim = subjectToken.getMayActClaim();

        // If no may_act claim, delegation is allowed by default
        // (subject to other authorization checks)
        if (mayActClaim == null) {
            LOGGER.debug("No may_act claim present, delegation allowed by default");
            return ValidationResult.valid();
        }

        if (!(mayActClaim instanceof Map)) {
            LOGGER.warn("Invalid may_act claim format, expected Map but got: {}",
                    mayActClaim.getClass().getSimpleName());
            return ValidationResult.invalid("Invalid may_act claim format");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mayAct = (Map<String, Object>) mayActClaim;

        // Validate actor subject
        ValidationResult subjectValidation = validateActorSubject(mayAct, actorToken.getSubject());
        if (!subjectValidation.isValid()) {
            return subjectValidation;
        }

        // Validate audience if specified in may_act
        ValidationResult audienceValidation = validateAudience(mayAct, targetAudience);
        if (!audienceValidation.isValid()) {
            return audienceValidation;
        }

        // Validate client_id if specified in may_act
        ValidationResult clientValidation = validateClientId(mayAct, actorToken.getClientId());
        if (!clientValidation.isValid()) {
            return clientValidation;
        }

        LOGGER.debug("may_act validation passed for actor: {}", actorToken.getSubject());
        return ValidationResult.valid();
    }

    /**
     * Validate that the actor's subject matches the allowed subject in may_act.
     */
    private ValidationResult validateActorSubject(Map<String, Object> mayAct, String actorSubject) {
        Object allowedSubject = mayAct.get(Claims.SUB);

        if (allowedSubject == null) {
            // No subject restriction
            return ValidationResult.valid();
        }

        if (allowedSubject instanceof String) {
            if (!allowedSubject.equals(actorSubject)) {
                LOGGER.debug("Actor subject '{}' does not match allowed subject '{}'",
                        actorSubject, allowedSubject);
                return ValidationResult.invalid(
                        "Actor is not authorized to act on behalf of this subject");
            }
        } else if (allowedSubject instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> allowedSubjects = (List<String>) allowedSubject;
            if (!allowedSubjects.contains(actorSubject)) {
                LOGGER.debug("Actor subject '{}' not in allowed subjects list", actorSubject);
                return ValidationResult.invalid(
                        "Actor is not in the list of authorized actors");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate that the target audience is allowed by may_act.
     */
    private ValidationResult validateAudience(Map<String, Object> mayAct, String targetAudience) {
        Object allowedAudience = mayAct.get(Claims.AUD);

        if (allowedAudience == null) {
            // No audience restriction
            return ValidationResult.valid();
        }

        if (targetAudience == null || targetAudience.isEmpty()) {
            // may_act specifies audience but request doesn't have one
            // This could be valid depending on policy, we'll allow it
            LOGGER.debug("may_act specifies audience constraints but no target audience provided");
            return ValidationResult.valid();
        }

        if (allowedAudience instanceof String) {
            if (!allowedAudience.equals(targetAudience)) {
                LOGGER.debug("Target audience '{}' does not match allowed audience '{}'",
                        targetAudience, allowedAudience);
                return ValidationResult.invalid(
                        "Target audience is not authorized for delegation");
            }
        } else if (allowedAudience instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> allowedAudiences = (List<String>) allowedAudience;
            if (!allowedAudiences.contains(targetAudience)) {
                LOGGER.debug("Target audience '{}' not in allowed audiences list", targetAudience);
                return ValidationResult.invalid(
                        "Target audience is not in the list of authorized audiences");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate that the actor's client_id is allowed by may_act.
     */
    private ValidationResult validateClientId(Map<String, Object> mayAct, String actorClientId) {
        Object allowedClientId = mayAct.get(Claims.CLIENT_ID);

        if (allowedClientId == null) {
            // No client_id restriction
            return ValidationResult.valid();
        }

        if (actorClientId == null || actorClientId.isEmpty()) {
            LOGGER.debug("may_act specifies client_id constraint but actor has no client_id");
            return ValidationResult.invalid(
                    "Actor client_id is required for this delegation");
        }

        if (allowedClientId instanceof String) {
            if (!allowedClientId.equals(actorClientId)) {
                LOGGER.debug("Actor client_id '{}' does not match allowed client_id '{}'",
                        actorClientId, allowedClientId);
                return ValidationResult.invalid(
                        "Actor client is not authorized to act on behalf of this subject");
            }
        } else if (allowedClientId instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> allowedClientIds = (List<String>) allowedClientId;
            if (!allowedClientIds.contains(actorClientId)) {
                LOGGER.debug("Actor client_id '{}' not in allowed client_ids list", actorClientId);
                return ValidationResult.invalid(
                        "Actor client is not in the list of authorized clients");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Check if a may_act claim is present and restrictive.
     *
     * @param mayActClaim the may_act claim
     * @return true if may_act is present and contains restrictions
     */
    public boolean hasMayActRestrictions(Object mayActClaim) {
        if (mayActClaim == null || !(mayActClaim instanceof Map)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mayAct = (Map<String, Object>) mayActClaim;

        return mayAct.containsKey(Claims.SUB) ||
               mayAct.containsKey(Claims.AUD) ||
               mayAct.containsKey(Claims.CLIENT_ID);
    }
}
