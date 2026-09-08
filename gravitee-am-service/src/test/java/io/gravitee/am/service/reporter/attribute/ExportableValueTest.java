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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.UserIdentity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class ExportableValueTest {

    @Nested
    class Scalars {

        static Stream<Object> scalars() {
            return Stream.of(
                    "a string",
                    new StringBuilder("a char sequence"),
                    42,
                    42L,
                    4.2d,
                    Boolean.TRUE,
                    'c',
                    Status.SUCCESS,
                    UUID.randomUUID(),
                    new Date(),
                    Instant.now(),
                    LocalDate.now());
        }

        @ParameterizedTest
        @MethodSource("scalars")
        void areExportable(Object value) {
            assertThat(ExportableValue.isExportable(value)).isTrue();
        }
    }

    @Nested
    class CollectionsOfScalars {

        @Test
        void areExportable() {
            assertThat(ExportableValue.isExportable(List.of("ADMIN", "USER"))).isTrue();
            assertThat(ExportableValue.isExportable(Set.of("group-a", "group-b"))).isTrue();
            assertThat(ExportableValue.isExportable(List.of(1, 2, 3))).isTrue();
        }

        @Test
        void anEmptyOneIsExportable() {
            assertThat(ExportableValue.isExportable(List.of())).isTrue();
        }
    }

    @Nested
    class Structures {

        static Stream<Object> structures() {
            return Stream.of(
                    Map.of("employeeId", "E-4471"),
                    Map.of(),
                    List.of(Map.of("providerId", "idp-1")),
                    List.of(new UserIdentity()),
                    new UserIdentity());
        }

        @ParameterizedTest
        @MethodSource("structures")
        void areNotExportable(Object value) {
            assertThat(ExportableValue.isExportable(value)).isFalse();
        }

        @Test
        void anArrayIsNotExportable() {
            assertThat(ExportableValue.isExportable(new String[]{"a", "b"})).isFalse();
        }
    }

    @Nested
    class NothingResolved {

        @Test
        void isNotExportable() {
            assertThat(ExportableValue.isExportable(null)).isFalse();
        }
    }
}
