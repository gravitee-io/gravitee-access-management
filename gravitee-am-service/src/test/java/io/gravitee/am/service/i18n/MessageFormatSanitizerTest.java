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
package io.gravitee.am.service.i18n;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
class MessageFormatSanitizerTest {

    @ParameterizedTest
    @MethodSource("dataSet")
    void checkSingleQuote(String msg, String exptedMsg) {
        Assertions.assertEquals(exptedMsg, MessageFormatSanitizer.sanitizeSingleQuote(msg));
    }

    static Stream<Arguments> dataSet() {
        return Stream.of(
                Arguments.arguments("Hello without single quote", "Hello without single quote"),
                Arguments.arguments("Single quote (') need to be double to avoid erasure by MessageFormatter", "Single quote ('') need to be double to avoid erasure by MessageFormatter"),
                Arguments.arguments("Single quote surrounded by spaces ( ')/( ' )/(' ) need to be double to avoid erasure by MessageFormatter", "Single quote surrounded by spaces ( '')/( '' )/('' ) need to be double to avoid erasure by MessageFormatter"),
                Arguments.arguments("Single quote (') need to be double to avoid erasure by MessageFormatter but not these ones '{0}'", "Single quote ('') need to be double to avoid erasure by MessageFormatter but not these ones '{0}'")
        );
    }
}
