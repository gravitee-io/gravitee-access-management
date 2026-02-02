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

/**
 * Information extracted from the validated actor token for delegation scenarios.
 * Used to build the "act" claim in the issued token per RFC 8693 Section 4.1.
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
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1</a>
 * @author GraviteeSource Team
 */
public record ActorTokenInfo(
        String subject,
        String gis,
        Object subjectTokenActClaim,
        Object actorTokenActClaim,
        int delegationDepth
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
     * Check if the actor token has an existing "act" claim (an actor is itself a delegated token).
     */
    public boolean hasActorTokenActClaim() {
        return actorTokenActClaim != null;
    }
}
