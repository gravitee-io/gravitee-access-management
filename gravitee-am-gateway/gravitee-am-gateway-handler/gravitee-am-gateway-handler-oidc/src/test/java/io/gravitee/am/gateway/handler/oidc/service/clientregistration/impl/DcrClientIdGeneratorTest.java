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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DcrClientIdGeneratorTest {

    private static final String KEY = "handlers.oidc.dcr.clientIdLength";

    @Test
    void should_generate_43_characters_url_safe_id_when_length_is_not_configured() {
        var generator = new DcrClientIdGenerator(new MockEnvironment());

        assertThat(generator.generate()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void should_generate_43_characters_url_safe_id_when_length_is_blank() {
        var generator = new DcrClientIdGenerator(new MockEnvironment().withProperty(KEY, "  "));

        assertThat(generator.generate()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 30, 255})
    void should_generate_alphanumeric_id_of_configured_length(int length) {
        var generator = new DcrClientIdGenerator(new MockEnvironment().withProperty(KEY, String.valueOf(length)));

        assertThat(generator.generate()).hasSize(length).matches("[A-Za-z0-9]+");
    }

    @Test
    void should_generate_distinct_ids() {
        var generator = new DcrClientIdGenerator(new MockEnvironment().withProperty(KEY, "30"));

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "256", "0", "-1", "abc", "30.5"})
    void should_fail_at_startup_when_length_is_invalid(String value) {
        var environment = new MockEnvironment().withProperty(KEY, value);

        assertThatThrownBy(() -> new DcrClientIdGenerator(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KEY)
                .hasMessageContaining("between 8 and 255");
    }
}
