/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.model.jose;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class KeyTypeTest {

    @ParameterizedTest
    @MethodSource("params_that_must_parse")
    public void parse(String keyType, KeyType expected) {
        assertEquals(expected, KeyType.parse(keyType));
    }

    private static Stream<Arguments> params_that_must_parse() {
        return Stream.of(
                Arguments.of("RSA", KeyType.RSA),
                Arguments.of("EC", KeyType.EC),
                Arguments.of("OCT", KeyType.OCT),
                Arguments.of("OKP", KeyType.OKP),
                Arguments.of("rsa", KeyType.RSA),
                Arguments.of("ec", KeyType.EC),
                Arguments.of("oct", KeyType.OCT),
                Arguments.of("okp", KeyType.OKP)
        );
    }

    @ParameterizedTest
    @MethodSource("params_that_must_not_parse")
    public void must_not_parse(String keyType, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> KeyType.parse(keyType));
    }

    private static Stream<Arguments> params_that_must_not_parse() {
        return Stream.of(
                Arguments.of(null, NullPointerException.class),
                Arguments.of("unknown", IllegalArgumentException.class)
        );
    }
}
