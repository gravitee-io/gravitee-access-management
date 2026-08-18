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

import java.util.List;
import java.util.Set;

/**
 * Rejects a token exchange claim mapping that would overwrite a protocol-critical claim on the
 * issued token. A mapping can read from an external trusted-issuer subject token, so a mapping onto
 * "sub" or "iss" would let a remote issuer redefine the identity of the exchanged token.
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
            Claims.SCOPE);

    public record ValidationResult(List<String> invalidClaims) {

        public boolean isInvalid() {
            return invalidClaims != null && !invalidClaims.isEmpty();
        }

        public static ValidationResult valid() {
            return new ValidationResult(List.of());
        }
    }

    @Override
    public ValidationResult validate(List<TokenExchangeClaimMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return ValidationResult.valid();
        }
        List<String> invalidClaims = mappings.stream()
                .map(TokenExchangeClaimMapping::getTokenClaim)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(RESERVED_TOKEN_CLAIMS::contains)
                .toList();
        return new ValidationResult(invalidClaims);
    }
}
