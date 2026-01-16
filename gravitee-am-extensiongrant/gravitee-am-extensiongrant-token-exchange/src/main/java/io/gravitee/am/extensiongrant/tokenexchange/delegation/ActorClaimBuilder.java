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
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for constructing the 'act' (actor) claim as defined in RFC 8693 Section 4.1.
 *
 * The actor claim provides a means within a JWT to express that delegation has occurred
 * and identify the acting party to whom authority has been delegated.
 *
 * Example of a delegation chain:
 * <pre>
 * {
 *   "aud": "https://consumer.example.com",
 *   "iss": "https://issuer.example.com",
 *   "exp": 1443904177,
 *   "sub": "user@example.com",
 *   "act": {
 *     "sub": "admin@example.com",
 *     "act": {
 *       "sub": "service-account@example.com"
 *     }
 *   }
 * }
 * </pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1 - "act" (Actor) Claim</a>
 * @author GraviteeSource Team
 */
public class ActorClaimBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorClaimBuilder.class);

    /**
     * Build the actor claim for a delegation scenario.
     *
     * @param actorToken the validated actor token
     * @param existingActClaim the existing 'act' claim from the subject token (for chained delegation)
     * @param configuration the extension grant configuration
     * @return the actor claim map
     * @throws InvalidGrantException if the delegation chain would exceed maximum depth
     */
    public Map<String, Object> buildActorClaim(ValidatedToken actorToken,
                                                Object existingActClaim,
                                                TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {

        // Calculate current delegation depth
        int currentDepth = calculateDelegationDepth(existingActClaim);
        int maxDepth = configuration.getMaxDelegationChainDepth();

        // Adding a new actor increases depth by 1
        if (currentDepth + 1 > maxDepth) {
            LOGGER.warn("Delegation chain depth ({}) would exceed maximum allowed ({})",
                    currentDepth + 1, maxDepth);
            throw new InvalidGrantException(
                    "Delegation chain depth would exceed maximum allowed: " + maxDepth);
        }

        // Build the new actor claim
        Map<String, Object> actClaim = new LinkedHashMap<>();

        // Required: actor's subject
        actClaim.put(Claims.SUB, actorToken.getSubject());

        // Optional: actor's client_id
        String clientId = actorToken.getClientId();
        if (clientId != null && !clientId.isEmpty()) {
            actClaim.put(Claims.CLIENT_ID, clientId);
        }

        // Optional: actor's issuer (useful for cross-domain delegation)
        String issuer = actorToken.getIssuer();
        if (issuer != null && !issuer.isEmpty()) {
            actClaim.put(Claims.ISS, issuer);
        }

        // Nest existing act claim if present (for chained delegation)
        if (existingActClaim != null) {
            actClaim.put(Claims.ACT, existingActClaim);
        }

        LOGGER.debug("Built actor claim for subject '{}' with delegation depth {}",
                actorToken.getSubject(), currentDepth + 1);

        return actClaim;
    }

    /**
     * Build a simple actor claim without chain validation.
     * Use this when you've already validated the delegation chain.
     *
     * @param actorSubject the actor's subject
     * @param actorClientId the actor's client_id (optional)
     * @param actorIssuer the actor's issuer (optional)
     * @param existingActClaim the existing 'act' claim (optional)
     * @return the actor claim map
     */
    public Map<String, Object> buildActorClaim(String actorSubject,
                                                String actorClientId,
                                                String actorIssuer,
                                                Object existingActClaim) {
        Map<String, Object> actClaim = new LinkedHashMap<>();

        actClaim.put(Claims.SUB, actorSubject);

        if (actorClientId != null && !actorClientId.isEmpty()) {
            actClaim.put(Claims.CLIENT_ID, actorClientId);
        }

        if (actorIssuer != null && !actorIssuer.isEmpty()) {
            actClaim.put(Claims.ISS, actorIssuer);
        }

        if (existingActClaim != null) {
            actClaim.put(Claims.ACT, existingActClaim);
        }

        return actClaim;
    }

    /**
     * Calculate the depth of an existing delegation chain.
     *
     * @param actClaim the 'act' claim to analyze
     * @return the depth of the delegation chain (0 if no delegation)
     */
    public int calculateDelegationDepth(Object actClaim) {
        if (actClaim == null) {
            return 0;
        }
        if (!(actClaim instanceof Map)) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> act = (Map<String, Object>) actClaim;
        Object nestedAct = act.get(Claims.ACT);

        return 1 + calculateDelegationDepth(nestedAct);
    }

    /**
     * Extract the full delegation chain as a list of actor subjects.
     *
     * @param actClaim the 'act' claim to analyze
     * @return list of actor subjects in order (most recent actor first)
     */
    public java.util.List<String> extractDelegationChain(Object actClaim) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        extractDelegationChainRecursive(actClaim, chain);
        return chain;
    }

    private void extractDelegationChainRecursive(Object actClaim, java.util.List<String> chain) {
        if (actClaim == null || !(actClaim instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> act = (Map<String, Object>) actClaim;

        Object subject = act.get(Claims.SUB);
        if (subject != null) {
            chain.add(subject.toString());
        }

        Object nestedAct = act.get(Claims.ACT);
        extractDelegationChainRecursive(nestedAct, chain);
    }

    /**
     * Check if an actor is present in the delegation chain.
     *
     * @param actClaim the 'act' claim to search
     * @param actorSubject the actor subject to find
     * @return true if the actor is in the chain
     */
    public boolean isActorInChain(Object actClaim, String actorSubject) {
        if (actClaim == null || !(actClaim instanceof Map)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> act = (Map<String, Object>) actClaim;

        Object subject = act.get(Claims.SUB);
        if (subject != null && subject.toString().equals(actorSubject)) {
            return true;
        }

        Object nestedAct = act.get(Claims.ACT);
        return isActorInChain(nestedAct, actorSubject);
    }
}
