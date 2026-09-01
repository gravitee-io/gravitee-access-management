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
package io.gravitee.am.common.spel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class SpelExpressionValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "{#user.email}",
            "{#user['email']}",
            "{#context.attributes['client']}",
            "{#user.additionalInformation['sub']}"
    })
    void shouldAcceptAWellFormedExpression(String expression) {
        assertTrue(SpelExpressionValidator.parses(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "urn:acme:{#user.id}",
            "{#user.email}-{#user.id}",
            "{#user.givenName} {#user.familyName}",
            "prefix-{#user.id}-suffix"
    })
    void shouldAcceptLiteralTextAroundAnExpression(String expression) {
        assertTrue(SpelExpressionValidator.parses(expression));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {
            "email",
            "emails.value",
            "a b c",
            "   "
    })
    void shouldAcceptAValueCarryingNoExpression(String expression) {
        assertTrue(SpelExpressionValidator.parses(expression));
    }

    @Test
    void shouldAcceptNothingToParse() {
        assertTrue(SpelExpressionValidator.parses(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{#user.email",
            "{#user['email']",
            "{#user.givenName} {#user.familyName"
    })
    void shouldRejectAnExpressionThatIsNeverClosed(String expression) {
        assertFalse(SpelExpressionValidator.parses(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{#user.}",
            "{#1 +}",
            "{#}",
            "{#user['email}"
    })
    void shouldRejectATypoInsideAClosedExpression(String expression) {
        assertFalse(SpelExpressionValidator.parses(expression));
    }

    @Test
    void shouldRejectABrokenExpressionSurroundedByLiteralText() {
        assertFalse(SpelExpressionValidator.parses("urn:acme:{#user.}"));
    }
}
