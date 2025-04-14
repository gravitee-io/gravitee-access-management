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

import io.gravitee.am.model.TokenClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.gravitee.am.common.oauth2.TokenTypeHint.ACCESS_TOKEN;
import static org.junit.jupiter.api.Assertions.*;

public class ApplicationTokenCustomClaimsValidatorTest {

    private final ApplicationTokenCustomClaimsValidator validator = new ApplicationTokenCustomClaimsValidator();


    @Test
    void shouldReturnValidWhenClaimListIsNull() {
        var result = validator.validate((List<TokenClaim>) null);
        assertFalse(result.isInvalid());
        assertTrue(result.invalidClaims().isEmpty());
    }

    @Test
    void shouldReturnValidWhenClaimListIsEmpty() {
        var result = validator.validate(List.of());
        assertFalse(result.isInvalid());
        assertTrue(result.invalidClaims().isEmpty());
    }

    @Test
    void shouldReturnInvalidWhenClaimContainsGis() {
        var claims = List.of(
                TokenClaim.of(ACCESS_TOKEN, "sub", "value"),
                TokenClaim.of(ACCESS_TOKEN, "gis", "value"),
                TokenClaim.of(ACCESS_TOKEN, "email", "value")
        );

        var result = validator.validate(claims);

        assertTrue(result.isInvalid());
        assertEquals(1, result.invalidClaims().size());
        assertEquals("gis", result.invalidClaims().get(0));
    }

    @Test
    void shouldReturnValidWhenClaimDoesntContainGis() {
        var claims = List.of(
                TokenClaim.of(ACCESS_TOKEN, "sub", "value"),
                TokenClaim.of(ACCESS_TOKEN, "email", "value")
        );

        var result = validator.validate(claims);

        assertFalse(result.isInvalid());
    }


}