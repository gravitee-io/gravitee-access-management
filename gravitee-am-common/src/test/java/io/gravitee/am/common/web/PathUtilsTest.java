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
package io.gravitee.am.common.web;

import io.gravitee.am.common.utils.PathUtils;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PathUtilsTest {

    @ParameterizedTest(name = "Must sanitize [{0}] into [{1}]")
    @MethodSource("params_that_must_sanitize")
    public void must_sanitize(String input, String expected){
        assertEquals(expected, PathUtils.sanitize(input));

    }

    public static Stream<Arguments> params_that_must_sanitize(){
        return Stream.of(
                Arguments.of(null, "/"),
                Arguments.of("", "/"),
                Arguments.of("              ", "/"),
                Arguments.of("\t\t\t", "/"),
                Arguments.of("\n", "/"),
                Arguments.of("\r\n", "/"),
                Arguments.of("/", "/"),
                Arguments.of("/test", "/test"),
                Arguments.of("////test/////", "/test"),
                Arguments.of("test", "/test")
        );
    }
}