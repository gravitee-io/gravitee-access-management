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
package io.gravitee.am.common.factor;

import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.gravitee.am.common.factor.FactorType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FactorTypeTest {


    public static Stream<Arguments> params_that_must_not_find_factor_type_from_string() {
        return Stream.of(
                Arguments.of("nein", NoSuchElementException.class),
                Arguments.of("nej", NoSuchElementException.class),
                Arguments.of("no", NoSuchElementException.class),
                Arguments.of("non", NoSuchElementException.class)
        );
    }

    public static Stream<Arguments> params_that_must_find_factor_type_from_string() {
        return Stream.of(
                Arguments.of("TOTP", OTP),
                Arguments.of("HTTP", HTTP),
                Arguments.of("SMS", SMS),
                Arguments.of("EMAIL", EMAIL),
                Arguments.of("CALL", CALL),
                Arguments.of("MOCK", MOCK),
                Arguments.of("totp", OTP),
                Arguments.of("http", HTTP),
                Arguments.of("sms", SMS),
                Arguments.of("email", EMAIL),
                Arguments.of("call", CALL),
                Arguments.of("mock", MOCK)
        );
    }

    @ParameterizedTest
    @MethodSource("params_that_must_find_factor_type_from_string")
    public void must_find_factor_type_from_string(String input, FactorType expected) {
        assertEquals(expected, getFactorTypeFromString(input));
    }

    @ParameterizedTest
    @MethodSource("params_that_must_not_find_factor_type_from_string")
    public void must_not_find_factor_type_from_string(String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> getFactorTypeFromString(input));
    }
}