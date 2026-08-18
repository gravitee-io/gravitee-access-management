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

import java.util.HashMap;
import java.util.Map;

/**
 * Information extracted from the validated subject token, exposed to EL custom token claims.
 * Present for both impersonation and delegation.
 *
 * @param subject the "sub" claim from the subject token
 * @param gis the "gis" (Gravitee Internal Subject) claim from the subject token, when present
 * @param claims the full claims map of the validated subject token. Exposed to EL custom token
 *               claims via {@code #context.attributes['token_exchange']['subject']['subject_token_claims']['<name>']}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
 * @author GraviteeSource Team
 */
public record SubjectTokenInfo(
        String subject,
        String gis,
        Map<String, Object> claims
) {
    /**
     * Check if the subject token has a "gis" claim.
     */
    public boolean hasGis() {
        return gis != null;
    }

    /**
     * Check if the subject token carries any claims (used to decide whether to publish them
     * to the EL execution context).
     */
    public boolean hasClaims() {
        return claims != null && !claims.isEmpty();
    }

    /**
     * Build the "subject" entry of the token exchange execution context.
     * The enclosing "token_exchange" wrapper is added by {@link TokenExchangeResult#buildExecutionContext()}.
     */
    public Map<String, Object> buildContext() {
        Map<String, Object> subjectContext = new HashMap<>();
        subjectContext.put(Claims.SUB, subject());

        if (hasGis()) {
            subjectContext.put(Claims.GIO_INTERNAL_SUB, gis());
        }

        if (hasClaims()) {
            subjectContext.put("subject_token_claims", claims());
        }

        return subjectContext;
    }
}
