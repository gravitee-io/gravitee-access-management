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
package io.gravitee.am.service.validators.claims;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static io.gravitee.am.service.validators.claims.ApplicationTokenCustomClaimsValidator.ValidationResult.valid;

@Component
public class ApplicationTokenCustomClaimsValidator implements Validator<List<TokenClaim>, ApplicationTokenCustomClaimsValidator.ValidationResult> {


    public record ValidationResult(List<String> invalidClaims) {

        public boolean isInvalid() {
            return invalidClaims != null && !invalidClaims.isEmpty();
        }

        public static ValidationResult valid(){
            return new ValidationResult(List.of());
        }
    }

    public ValidationResult validate(Application application){
        List<TokenClaim> tokenClaims = Optional.ofNullable(application.getSettings())
                .map(ApplicationSettings::getOauth)
                .map(ApplicationOAuthSettings::getTokenCustomClaims)
                .orElseGet(List::of);
        return validate(tokenClaims);
    }

    @Override
    public ValidationResult validate(List<TokenClaim> claims) {
        if(claims == null || claims.isEmpty()) {
            return valid();
        }
        List<String> invalidClaims = claims.stream()
                .map(TokenClaim::getClaimName)
                .map(String::trim)
                .filter(Claims.GIO_INTERNAL_SUB::equals)
                .toList();
        return new ValidationResult(invalidClaims);
    }
}
