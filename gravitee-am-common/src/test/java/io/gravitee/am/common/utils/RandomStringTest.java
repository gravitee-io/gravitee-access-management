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
package io.gravitee.am.common.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RandomStringTest {

    @Test
    public void shouldRecognizeValidUuidFromGenerator() {
        String id = RandomString.generate();

        assertThat(RandomString.isUuid(id)).isTrue();
    }

    @Test
    public void shouldReturnFalseForNull() {
        assertThat(RandomString.isUuid(null)).isFalse();
    }

    @Test
    public void shouldReturnFalseForEmptyString() {
        assertThat(RandomString.isUuid("")).isFalse();
    }

    @Test
    public void shouldReturnFalseForNonUuidString() {
        assertThat(RandomString.isUuid("not-a-uuid")).isFalse();
    }

    @Test
    public void shouldReturnFalseForMalformedUuid() {
        assertThat(RandomString.isUuid("1234-5678-90ab-cdef")).isFalse();
    }
}
