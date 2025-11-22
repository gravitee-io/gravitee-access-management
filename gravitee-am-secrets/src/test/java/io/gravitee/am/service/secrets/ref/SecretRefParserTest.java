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
package io.gravitee.am.service.secrets.ref;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author GraviteeSource Team
 */
public class SecretRefParserTest {

    @ParameterizedTest
    @MethodSource("validTestCases")
    public void shouldParseSecretRef(String input, SecretRef expectedRef) {
        SecretRef parsedRef = SecretRefParser.parse(input);
        assertThat(parsedRef).isEqualTo(expectedRef);
    }

    public static Stream<Arguments> validTestCases() {
        return Stream.of(
                Arguments.of("/vault/secrets/passwords", new SecretRef("vault", "secrets/passwords", null, ArrayListMultimap.create())),
                Arguments.of("/vault/secrets/passwords:redis", new SecretRef("vault", "secrets/passwords", "redis", ArrayListMultimap.create())),
                Arguments.of("/vault/secrets/passwords:redis?reloadOnChange=true&renewable=true", new SecretRef("vault", "secrets/passwords", "redis", ImmutableListMultimap.<String, String>builder()
                        .put("reloadOnChange", "true")
                        .put("renewable", "true")
                        .build()
                ))
        );
    }
}
