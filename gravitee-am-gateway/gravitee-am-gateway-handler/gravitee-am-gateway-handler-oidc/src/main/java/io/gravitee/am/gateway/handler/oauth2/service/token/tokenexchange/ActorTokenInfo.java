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

import io.gravitee.am.common.jwt.Claims;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Information extracted from the validated actor token for delegation scenarios.
 * Used to build the "act" claim in the issued token per RFC 8693 Section 4.1
 * and to expose the actor token payload to EL custom token claims.
 *
 * @param subject the "sub" claim from the actor token (required per RFC 8693)
 * @param gis the "gis" (Gravitee Internal Subject) claim from the actor token.
 *            Used to identify the actor in V2 domains where "sub" alone is not sufficient.
 * @param subjectTokenActClaim the existing "act" claim from the subject token for delegation chains.
 *                              Per RFC 8693, when the subject token already has an "act" claim,
 *                              it represents the prior delegation chain that should be nested
 *                              under the current actor in the new token.
 * @param actorTokenActClaim the existing "act" claim from the actor token itself.
 *                            When the actor token is also a delegated token, this captures
 *                            the actor's own delegation chain for complete audit traceability.
 *                            Stored as "actor_act" in the issued token's "act" claim.
 * @param delegationDepth the current delegation depth (number of nested "act" claims + 1)
 * @param claims the full claims map of the validated actor token. Exposed to EL custom token
 *               claims via {@code #context.attributes['token_exchange']['actor']['actor_token_claims']['<name>']}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1</a>
 * @author GraviteeSource Team
 */
public record ActorTokenInfo(
        String subject,
        String gis,
        String subProfile,
        Object subjectTokenActClaim,
        Object actorTokenActClaim,
        int delegationDepth,
        Map<String, Object> claims
) {
    /**
     * Check if the subject token has an existing "act" claim (part of a prior delegation chain).
     */
    public boolean hasSubjectTokenActClaim() {
        return subjectTokenActClaim != null;
    }

    /**
     * Check if the actor token has a "gis" claim.
     */
    public boolean hasGis() {
        return gis != null;
    }

    /**
     * Check if the actor token carries a {@code sub_profile} claim (e.g. an agent profile).
     * Per RFC entity-profiles draft, only profiled actors propagate sub_profile in the act node.
     */
    public boolean hasSubProfile() {
        return StringUtils.hasLength(subProfile);
    }

    /**
     * Check if the actor token has an existing "act" claim (an actor is itself a delegated token).
     */
    public boolean hasActorTokenActClaim() {
        return actorTokenActClaim != null;
    }

    /**
     * Check if the actor token carries any claims (used to decide whether to publish them
     * to the EL execution context).
     */
    public boolean hasClaims() {
        return claims != null && !claims.isEmpty();
    }

    public Map<String, Object> buildTokenExchangeExecutionContext( ) {
        Map<String, Object> actorContext = new HashMap<>();
        actorContext.put(Claims.SUB, subject());
        actorContext.put("delegation_depth", delegationDepth());

        if (hasGis()) {
            actorContext.put(Claims.GIO_INTERNAL_SUB, gis());
        }

        if (hasSubjectTokenActClaim()) {
            actorContext.put("subject_token_act", subjectTokenActClaim());
        }

        if (hasActorTokenActClaim()) {
            actorContext.put("actor_token_act", actorTokenActClaim());
        }

        if (hasClaims()) {
            actorContext.put("actor_token_claims", claims());
        }

        return Map.of("token_exchange", Map.of("actor", actorContext));
    }
}
