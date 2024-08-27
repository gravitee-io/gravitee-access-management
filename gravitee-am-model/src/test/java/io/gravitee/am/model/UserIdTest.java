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
package io.gravitee.am.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class UserIdTest {


    public static Stream<Arguments> cases() {
        return Stream.of(
                arguments("loremipsum", UserId.internal("loremipsum")),
                arguments("lorem:ipsum", new UserId(null, "ipsum","lorem")),
                arguments("lorem:ipsum:withanothercolon", new UserId(null, "ipsum:withanothercolon", "lorem"))
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void parse(String rawId, UserId parsed) {
        assertThat(UserId.parse(rawId)).isEqualTo(parsed);
    }

    @Test
    void exceptionWhenMalformed() {
        assertThrows(IllegalArgumentException.class, () -> UserId.parse("invalid:"));
    }
}
