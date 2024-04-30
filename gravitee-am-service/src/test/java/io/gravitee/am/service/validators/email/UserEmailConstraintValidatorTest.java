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
package io.gravitee.am.service.validators.email;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class UserEmailConstraintValidatorTest {


    static Arguments[] passCases() {
        return new Arguments[]{
                arguments(true, "test@gio.localhost"),
                arguments(false, "test@gio.localhost"),
                arguments(false, ""),
                arguments(false, "     "),
                arguments(false, null),
        };
    }

    static Arguments[] failCases() {
        return new Arguments[]{
                arguments(true, ""),
                arguments(true, "     "),
                arguments(true, null),
                arguments(true, veryLongEmail()),
                arguments(false, veryLongEmail())
        };
    }

    private static String veryLongEmail() {
        // aaa...aaa@localhost
        return IntStream.range(0, EmailValidatorImpl.EMAIL_MAX_LENGTH)
                .mapToObj(i -> "a")
                .collect(Collectors.joining()) + "@localhost";

    }

    @ParameterizedTest
    @MethodSource("passCases")
    void validEmail_shouldPass(boolean emailRequired, String email) {
        var validator = new UserEmailConstraintValidator(testEnvironment(emailRequired));
        var context = mockValidatorContext();
        assertThat(validator.isValid(email, context)).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }


    @ParameterizedTest
    @MethodSource("failCases")
    void invalidEmail_shouldFail(boolean emailRequired, String email) {
        var validator = new UserEmailConstraintValidator(testEnvironment(emailRequired));
        var context = mockValidatorContext();
        assertThat(validator.isValid(email, context)).isFalse();
        verify(context).buildConstraintViolationWithTemplate(anyString());
    }

    private ConstraintValidatorContext mockValidatorContext() {
        var context = mock(ConstraintValidatorContext.class);
        when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));
        return context;
    }


    private Environment testEnvironment(boolean emailRequired) {
        return new MockEnvironment().withProperty(UserEmail.PROPERTY_USER_EMAIL_REQUIRED, Boolean.toString(emailRequired));
    }
}