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
package io.gravitee.am.service.utils;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WildcardUtilsTest {

    @ParameterizedTest(name = "Must transform [{0}] into [{1}]")
    @MethodSource("params_that_must_transform_wildcard_to_regex")
    void must_transform_wildcard_to_regex(String input, String expected) {
        assertEquals(expected, WildcardUtils.toRegex(input));
    }

    public static Stream<Arguments> params_that_must_transform_wildcard_to_regex() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("*", ".*"),
                Arguments.of("**", ".*.*"),
                Arguments.of("*.*", ".*\\Q.\\E.*"),
                Arguments.of("https://*.gravitee.io/*", "\\Qhttps://\\E.*\\Q.gravitee.io/\\E.*"),
                Arguments.of("https://fixed.gravitee.io/", "\\Qhttps://fixed.gravitee.io/\\E"),
                Arguments.of("*@*.*", ".*\\Q@\\E.*\\Q.\\E.*"),
                Arguments.of("*@*.com", ".*\\Q@\\E.*\\Q.com\\E")
        );
    }
}
