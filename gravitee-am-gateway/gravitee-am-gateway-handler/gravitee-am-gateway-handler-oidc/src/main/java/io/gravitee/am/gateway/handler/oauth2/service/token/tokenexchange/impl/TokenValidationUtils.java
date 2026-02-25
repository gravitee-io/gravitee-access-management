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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TrustedIssuer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared claim parsing, temporal validation, and token building utilities
 * for token exchange validators.
 *
 * @author GraviteeSource Team
 */
final class TokenValidationUtils {

    private TokenValidationUtils() {}

    @SuppressWarnings("unchecked")
    static Set<String> parseScopes(Object scopeClaim) {
        return switch (scopeClaim) {
            case null -> Collections.emptySet();
            case String s -> new HashSet<>(Arrays.asList(s.split("\\s+")));
            case List<?> list -> new HashSet<>((List<String>) list);
            default -> Collections.emptySet();
        };
    }

    @SuppressWarnings("unchecked")
    static List<String> parseAudience(Object audClaim) {
        return switch (audClaim) {
            case null -> Collections.emptyList();
            case String s -> Collections.singletonList(s);
            case List<?> list -> (List<String>) list;
            default -> Collections.emptyList();
        };
    }

    static void validateTemporalClaims(long exp, long nbf, String tokenType) {
        long currentTime = System.currentTimeMillis() / 1000;
        if (exp > 0 && exp < currentTime) {
            throw new InvalidGrantException(tokenType + " has expired");
        }
        if (nbf > 0 && nbf > currentTime) {
            throw new InvalidGrantException(tokenType + " is not yet valid");
        }
    }

    static ValidatedToken buildValidatedToken(Map<String, Object> claims,
                                               long exp, long iat, long nbf,
                                               Set<String> scopes, List<String> audience,
                                               String tokenType, Domain domain,
                                               TrustedIssuer trustedIssuer) {
        return ValidatedToken.builder()
                .subject(Objects.toString(claims.get(Claims.SUB), null))
                .issuer(Objects.toString(claims.get(Claims.ISS), null))
                .claims(claims)
                .scopes(scopes)
                .expiration(exp > 0 ? new Date(exp * 1000) : null)
                .issuedAt(iat > 0 ? new Date(iat * 1000) : null)
                .notBefore(nbf > 0 ? new Date(nbf * 1000) : null)
                .tokenId(Objects.toString(claims.get(Claims.JTI), null))
                .audience(audience)
                .clientId(Objects.toString(claims.get(Claims.CLIENT_ID), null))
                .tokenType(tokenType)
                .domain(domain.getId())
                .trustedIssuer(trustedIssuer)
                .build();
    }
}
