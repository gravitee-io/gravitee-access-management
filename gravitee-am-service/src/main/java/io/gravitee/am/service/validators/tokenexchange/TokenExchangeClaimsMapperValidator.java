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
import org.springframework.stereotype.Component;

import java.util.Collections;
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
 * "sub" or "iss" would let a remote issuer redefine the identity of the exchanged token.
 * <p>
 * This guards against a misconfigured mapping, not against the administrator. A tokenCustomClaims
 * entry naming the same target reaches the same claim through the expression language, reading the
 * subject token via {@code #context.attributes['token_exchange']['subject']['subject_token_claims']},
 * and only "gis" is refused there. An administrator who can write custom claims can already set any
 * of these names.
 */
@Component
public class TokenExchangeClaimsMapperValidator implements Validator<List<TokenExchangeClaimMapping>, TokenExchangeClaimsMapperValidator.ValidationResult> {

    private static final Set<String> RESERVED_TOKEN_CLAIMS = Set.of(
            Claims.GIO_INTERNAL_SUB,
            Claims.SUB,
            Claims.ISS,
            Claims.AUD,
            Claims.EXP,
            Claims.IAT,
            Claims.NBF,
            Claims.JTI,
            Claims.ACT,
            Claims.CLIENT_ID,
            Claims.SCOPE,
            // authentication context the relying party makes security decisions on
            Claims.AUTH_TIME,
            NONCE,
            ACR,
            AMR,
            AZP,
            // agent identity, written later in issuance only when still absent, so a mapping
            // here suppresses the value AM would have advertised
            Claims.CLIENT_PROFILE,
            Claims.SUB_PROFILE);

    public record ValidationResult(List<String> invalidClaims, List<String> duplicateClaims) {

        public boolean isInvalid() {
            return !invalidClaims.isEmpty() || !duplicateClaims.isEmpty();
        }

        public static ValidationResult valid() {
            return new ValidationResult(List.of(), List.of());
        }

        public String describe() {
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

        return new ValidationResult(invalidClaims, duplicateClaims);
    }
}
