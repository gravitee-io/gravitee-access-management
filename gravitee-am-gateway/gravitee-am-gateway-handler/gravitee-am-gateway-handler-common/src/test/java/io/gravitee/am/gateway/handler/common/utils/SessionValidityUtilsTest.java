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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.model.User;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static io.gravitee.am.gateway.handler.common.utils.SessionValidityUtils.isIssuedBeforeUserInvalidation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class SessionValidityUtilsTest {

    private static final long ISSUED_AT = 1_700_000_000_000L;

    @Test
    void shouldBeValid_noInvalidatingOperation() {
        assertFalse(isIssuedBeforeUserInvalidation(new User(), ISSUED_AT));
    }

    @Test
    void shouldBeInvalid_passwordResetAfterIssue() {
        final var user = new User();
        user.setLastPasswordReset(new Date(ISSUED_AT + 5000L));
        assertTrue(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }

    @Test
    void shouldBeInvalid_usernameResetAfterIssue() {
        final var user = new User();
        user.setLastUsernameReset(new Date(ISSUED_AT + 5000L));
        assertTrue(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }

    @Test
    void shouldBeInvalid_logoutAfterIssue() {
        final var user = new User();
        user.setLastLogoutAt(new Date(ISSUED_AT + 5000L));
        assertTrue(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }

    @Test
    void shouldBeValid_operationBeforeIssue() {
        final var user = new User();
        user.setLastPasswordReset(new Date(ISSUED_AT - 5000L));
        assertFalse(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }

    @Test
    void shouldBeValid_operationWithinTheTolerance() {
        final var user = new User();
        user.setLastPasswordReset(new Date(ISSUED_AT + 1000L));
        assertFalse(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }

    @Test
    void shouldBeInvalid_operationJustOverTheTolerance() {
        final var user = new User();
        user.setLastPasswordReset(new Date(ISSUED_AT + 1001L));
        assertTrue(isIssuedBeforeUserInvalidation(user, ISSUED_AT));
    }
}
