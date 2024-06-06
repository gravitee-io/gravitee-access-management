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
package io.gravitee.am.service.validators;

import io.gravitee.am.service.spring.email.EmailConfiguration;
import io.gravitee.am.service.validators.email.EmailDomainValidatorImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailDomainValidatorTest {

    @Mock
    private ConfigurableEnvironment environment;

    @ParameterizedTest
    @MethodSource("params_that_must_validate_email")
    void must_validate_email(String email, boolean expectedResult) {
        when(environment.getProperty(eq("email.allowedfrom[0]"), eq(String.class))).thenReturn("*@example.com");
        when(environment.getProperty(eq("email.allowedfrom[1]"), eq(String.class))).thenReturn("another@mail.com");
        var validator = new EmailDomainValidatorImpl(getEmailConfiguration());
        assertEquals(expectedResult, validator.validate(email));
    }

    private static Stream<Arguments> params_that_must_validate_email() {
        return Stream.of(
                Arguments.of("test@example.com", true),
                Arguments.of("another@mail.com", true),
                Arguments.of("evil@mail.com", false),
                Arguments.of("invalid.email", false),
                Arguments.of("", false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("params_that_must_validate_email_with_default_allow_list")
    void must_validate_email_with_default_allow_list(String email, boolean expectedResult) {
        lenient().when(environment.getProperty(eq("email.allowlist[0]"), eq(String.class))).thenReturn(null);
        var validator = new EmailDomainValidatorImpl(getEmailConfiguration());

        assertEquals(expectedResult, validator.validate(email));
    }

    private static Stream<Arguments> params_that_must_validate_email_with_default_allow_list() {
        return Stream.of(
                Arguments.of("test@example.com", true),
                Arguments.of("another@mail.com", true),
                Arguments.of("evil@mail.com", true),
                Arguments.of("invalid.email", false),
                Arguments.of("", false),
                Arguments.of(null, false)
        );
    }

    private EmailConfiguration getEmailConfiguration() {
        return new EmailConfiguration(environment);
    }
}
