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

package io.gravitee.am.gateway.handler.root.resources.endpoint;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.redirectMatches;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

public class ParamUtilsTest {

    @ParameterizedTest(name = "[{0}] matches [{1}] with strict: {2} --> [{3}] ")
    @MethodSource("params_that_must_redirect_matches_or_not")
    public void must_redirect_matches_or_not(String requestRedirectUri, String registeredRedirectUri, boolean strict, boolean expected) {
        assertEquals(expected, redirectMatches(requestRedirectUri, registeredRedirectUri, strict));
    }

    private static Stream<Arguments> params_that_must_redirect_matches_or_not() {
        return Stream.of(
                Arguments.of("https://test.com/department/", "https://test.com/department/", true, true),
                Arguments.of("https://test.com/department", "https://test.com/department/", true, false),
                Arguments.of("https://test.com/department/business", "https://test.com/department/*", false, true),
                Arguments.of("https://pre.test.com/department/business", "https://*.test.com/department/*", false, true),
                Arguments.of("https://test.com/other/business", "https://test.com/department/*", false, false),
                Arguments.of("https://test.com?id=10", "https://test.com/*", false, true),
                Arguments.of("https://test.com/department?id=10", "https://test.com/department*", false, true),
                Arguments.of("https://test.com?id=10", "https://test.com/department*", false, false),
                Arguments.of("https://test.com?id=10", "https://test.com", true, false),
                Arguments.of("https://test.com/people", "https://test.com", true, false),
                Arguments.of("https://test.com/people?v=123", "https://test.com", true, false)
        );
    }
}