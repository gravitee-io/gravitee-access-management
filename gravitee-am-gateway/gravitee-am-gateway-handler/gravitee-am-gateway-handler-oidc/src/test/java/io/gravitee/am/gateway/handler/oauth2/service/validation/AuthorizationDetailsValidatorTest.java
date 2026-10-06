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
package io.gravitee.am.gateway.handler.oauth2.service.validation;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidAuthorizationDetailsException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationDetailsValidatorTest {

    private final AuthorizationDetailsValidator validator = new AuthorizationDetailsValidator();

    @Test
    void shouldAcceptAbsentAuthorizationDetails() {
        assertDoesNotThrow(() -> validator.validate(null));
    }

    @Test
    void shouldAcceptEmptyAuthorizationDetails() {
        assertDoesNotThrow(() -> validator.validate(List.of()));
    }

    @Test
    void shouldRefuseAuthorizationDetailsOfAnyType() {
        assertRefused(List.of(Map.of("type", "payment_initiation")), "Assertion authorization_details type is not supported");
    }

    @Test
    void shouldRefuseAuthorizationDetailsEntryWithoutType() {
        assertRefused(List.of(Map.of("actions", List.of("read"))), "Assertion authorization_details type is not supported");
    }

    @Test
    void shouldRefuseAuthorizationDetailsObject() {
        assertRefused(Map.of("type", "payment_initiation"), "Assertion authorization_details is malformed");
    }

    @Test
    void shouldRefuseAuthorizationDetailsString() {
        assertRefused("payment_initiation", "Assertion authorization_details is malformed");
    }

    @Test
    void shouldRefuseAuthorizationDetailsNumber() {
        assertRefused(42L, "Assertion authorization_details is malformed");
    }

    private void assertRefused(Object authorizationDetails, String description) {
        InvalidAuthorizationDetailsException refusal = assertThrows(InvalidAuthorizationDetailsException.class,
                () -> validator.validate(authorizationDetails));
        assertEquals(description, refusal.getMessage());
        assertEquals("invalid_authorization_details", refusal.getOAuth2ErrorCode());
    }
}
