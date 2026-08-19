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
package io.gravitee.am.service.validators.tokenexchange;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.model.application.TokenExchangeClaimMapping;
import io.gravitee.am.service.validators.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.gravitee.am.common.oidc.idtoken.Claims.ACR;
import static io.gravitee.am.common.oidc.idtoken.Claims.AMR;
import static io.gravitee.am.common.oidc.idtoken.Claims.AZP;
import static io.gravitee.am.common.oidc.idtoken.Claims.NONCE;

/**
 * Rejects a token exchange claim mapping that would overwrite a protocol-critical claim on the
 * issued token. A mapping can read from an external trusted-issuer subject token, so a mapping onto
 * "sub" or "iss" would let a remote issuer redefine the identity of the exchanged token. The same
 * holds for "cnf", which carries the DPoP and mTLS sender constraint, and for "domain", which
 * DomainTokenValidator reads to decide whether a token is in scope for the revocation lookup.
 * <p>
 * This guards against a misconfigured mapping, not against the administrator. A tokenCustomClaims
 * entry naming the same target reaches the same claim through the expression language, reading the
 * subject token via {@code #context.attributes['token_exchange']['subject']['subject_token_claims']},
 * and only "gis" is refused there. An administrator who can write custom claims can already set any
 * of these names.
 */
@Component
public class TokenExchangeClaimMappingsValidator implements Validator<List<TokenExchangeClaimMapping>, TokenExchangeClaimMappingsValidator.ValidationResult> {

    // UMA 2.0 and RFC 9396 claims, written by TokenServiceImpl before the mapper runs. Neither has
    // a shared constant — both are private to the gateway.
    private static final String PERMISSIONS = "permissions";
    private static final String AUTHORIZATION_DETAILS = "authorization_details";

    private static final Set<String> RESERVED_TOKEN_CLAIMS = reservedTokenClaims();

    private static Set<String> reservedTokenClaims() {
        // Claims.getAllClaims() is the list AM already treats as its own when rebuilding a refresh
        // token, and covers iss, sub, aud, exp, nbf, iat, auth_time, updated_at, jti, domain,
        // claims_request_parameter, ip_address, user_agent, scope, cnf and client_id
        Set<String> reserved = new HashSet<>(Claims.getAllClaims());
        reserved.addAll(Set.of(
                Claims.GIO_INTERNAL_SUB,
                Claims.ACT,
                // authentication context the relying party makes security decisions on
                NONCE,
                ACR,
                AMR,
                AZP,
                // resource access decided at authorization time and read back after issuance
                PERMISSIONS,
                AUTHORIZATION_DETAILS,
                // agent identity, written later in issuance only when still absent, so a mapping
                // here suppresses the value AM would have advertised
                Claims.CLIENT_PROFILE,
                Claims.SUB_PROFILE));
        return Set.copyOf(reserved);
    }

    private final int maxCount;

    public TokenExchangeClaimMappingsValidator(@Value("${domain.tokenExchange.claimMappings.maxCount:20}") int maxCount) {
        this.maxCount = maxCount;
    }

    public record ValidationResult(List<String> invalidClaims, List<String> duplicateClaims, Integer exceededMaxCount) {

        public boolean isInvalid() {
            return !invalidClaims.isEmpty() || !duplicateClaims.isEmpty() || exceededMaxCount != null;
        }

        public static ValidationResult valid() {
            return new ValidationResult(List.of(), List.of(), null);
        }

        public String describe() {
            if (exceededMaxCount != null) {
                return "Maximum number of token exchange claim mappings exceeded (max: " + exceededMaxCount + ")";
            }
            return invalidClaims.isEmpty()
                    ? "Duplicate token exchange claim mappings: " + duplicateClaims
                    : "Invalid token exchange claim mappings: " + invalidClaims;
        }
    }

    @Override
    public ValidationResult validate(List<TokenExchangeClaimMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return ValidationResult.valid();
        }
        if (mappings.size() > maxCount) {
            return new ValidationResult(List.of(), List.of(), maxCount);
        }

        // a mapping without a target claim is ignored rather than rejected, matching the gateway,
        // which skips it instead of failing the exchange
        List<String> targets = mappings.stream()
                .map(TokenExchangeClaimMapping::getTokenClaim)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(target -> !target.isEmpty())
                .toList();

        List<String> invalidClaims = targets.stream()
                .filter(RESERVED_TOKEN_CLAIMS::contains)
                .distinct()
                .toList();

        List<String> duplicateClaims = targets.stream()
                .filter(target -> Collections.frequency(targets, target) > 1)
                .distinct()
                .toList();

        return new ValidationResult(invalidClaims, duplicateClaims, null);
    }
}
