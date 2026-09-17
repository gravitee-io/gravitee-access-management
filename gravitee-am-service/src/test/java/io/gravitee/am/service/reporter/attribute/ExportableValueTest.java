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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class ExportableValueTest {

    @Nested
    class Scalars {

        static Stream<Object> scalars() {
            return Stream.of(
                    "a string",
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
        void areExportedAsTheyAre(Object value) {
            assertThat(ExportableValue.of(value)).contains(value);
        }

        @Test
        void aCharSequenceIsExportedAsAString() {
            assertThat(ExportableValue.of(new StringBuilder("a char sequence"))).contains("a char sequence");
        }

        @Test
        void aDateIsExportedAsACopy() {
            Date date = new Date(1_000L);

            Object exported = ExportableValue.of(date).orElseThrow();
            date.setTime(2_000L);

            assertThat(exported).isEqualTo(new Date(1_000L));
        }
    }

    @Nested
    class CollectionValues {

        @Test
        void aListKeepsItsOrder() {
            assertThat(ExportableValue.of(List.of("USER", "ADMIN"))).contains(List.of("USER", "ADMIN"));
        }

        @Test
        void aSetIsExportedAsASortedList() {
            assertThat(ExportableValue.of(Set.of("USER", "ADMIN", "AUDITOR")))
                    .contains(List.of("ADMIN", "AUDITOR", "USER"));
        }

        @Test
        void aSetThatCannotBeSortedKeepsItsOwnOrder() {
            Set<Object> mixed = new LinkedHashSet<>(List.of("b", 1, "a"));

            assertThat(ExportableValue.of(mixed)).contains(List.of("b", 1, "a"));
        }

        @Test
        void anEmptyOneIsExported() {
            assertThat(ExportableValue.of(List.of())).contains(List.of());
        }

        @Test
        void aNullElementIsKept() {
            assertThat(ExportableValue.of(Arrays.asList("a", null))).contains(Arrays.asList("a", null));
        }

        @Test
        void listsNest() {
            assertThat(ExportableValue.of(List.of(List.of(1, 2), List.of(3))))
                    .contains(List.of(List.of(1, 2), List.of(3)));
        }
    }

    @Nested
    class Maps {

        @Test
        void keepTheirOrderAndNesting() {
            Map<String, Object> idp = new HashMap<>();
            idp.put("name", "azure");
            idp.put("groups", List.of("a", "b"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("employeeId", "E-4471");
            claims.put("idp", idp);

            Object exported = ExportableValue.of(claims).orElseThrow();

            assertThat(exported).isEqualTo(claims);
            assertThat(new ArrayList<Object>(((Map<?, ?>) exported).keySet())).containsExactly("employeeId", "idp");
        }

        @Test
        void anEmptyOneIsExported() {
            assertThat(ExportableValue.of(Map.of())).contains(Map.of());
        }

        @Test
        void aNullValueIsKept() {
            Map<String, Object> map = new HashMap<>();
            map.put("absent", null);

            assertThat(ExportableValue.of(map)).contains(map);
        }

        @Test
        void aListOfMapsIsExported() {
            assertThat(ExportableValue.of(List.of(Map.of("providerId", "idp-1"))))
                    .contains(List.of(Map.of("providerId", "idp-1")));
        }

        @Test
        void aKeyThatIsNotTextIsNotExported() {
            assertThat(ExportableValue.of(Map.of(1, "one"))).isEmpty();
            assertThat(ExportableValue.of(Map.of("nested", Map.of(Status.SUCCESS, "ok")))).isEmpty();
        }
    }

    @Nested
    class TheCopy {

        @Test
        void isDetachedFromItsSource() {
            Map<String, Object> nested = new HashMap<>(Map.of("name", "azure"));
            Map<String, Object> source = new HashMap<>(Map.of("idp", nested));
            List<String> groups = new ArrayList<>(List.of("a"));
            source.put("groups", groups);

            Object exported = ExportableValue.of(source).orElseThrow();
            nested.put("name", "changed");
            groups.add("b");
            source.put("added", "later");

            assertThat(exported).isEqualTo(Map.of("idp", Map.of("name", "azure"), "groups", List.of("a")));
        }

        @Test
        void cannotBeModified() {
            @SuppressWarnings("unchecked")
            Map<String, Object> exported = (Map<String, Object>) ExportableValue.of(
                    Map.of("groups", List.of("a"))).orElseThrow();

            assertThatThrownBy(() -> exported.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> ((List<Object>) exported.get("groups")).add("b"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ObjectValues {

        static Stream<Object> objects() {
            return Stream.of(
                    new UserIdentity(),
                    List.of(new UserIdentity()),
                    Map.of("idp-1", new UserIdentity()),
                    Map.of("nested", List.of("fine", new UserIdentity())),
                    new Object());
        }

        @ParameterizedTest
        @MethodSource("objects")
        void areNotExportedAnywhereInTheValue(Object value) {
            assertThat(ExportableValue.of(value)).isEmpty();
        }

        @Test
        void anArrayIsNotExported() {
            assertThat(ExportableValue.of(new String[]{"a", "b"})).isEmpty();
        }
    }

    @Nested
    class Limits {

        @Test
        void theMaximumDepthIsExported() {
            assertThat(ExportableValue.of(nestedMaps(ExportableValue.MAX_DEPTH))).isPresent();
        }

        @Test
        void anythingDeeperIsNotExported() {
            assertThat(ExportableValue.of(nestedMaps(ExportableValue.MAX_DEPTH + 1))).isEmpty();
        }

        @Test
        void aValueThatContainsItselfIsNotExported() {
            Map<String, Object> cyclic = new HashMap<>();
            cyclic.put("self", cyclic);
            List<Object> cyclicList = new ArrayList<>();
            cyclicList.add(cyclicList);

            assertThat(ExportableValue.of(cyclic)).isEmpty();
            assertThat(ExportableValue.of(cyclicList)).isEmpty();
        }

        @Test
        void theMaximumNumberOfElementsIsExported() {
            assertThat(ExportableValue.of(elements(ExportableValue.MAX_NODES))).isPresent();
        }

        @Test
        void anyMoreIsNotExported() {
            assertThat(ExportableValue.of(elements(ExportableValue.MAX_NODES + 1))).isEmpty();
        }

        @Test
        void elementsAreCountedAcrossTheWholeValue() {
            int half = ExportableValue.MAX_NODES / 2;
            // each inner list is itself an element of the outer one
            assertThat(ExportableValue.of(List.of(elements(half - 1), elements(half - 1)))).isPresent();
            assertThat(ExportableValue.of(List.of(elements(half), elements(half)))).isEmpty();
        }

        /**
         * @return a map with {@code depth} levels of maps nested inside it
         */
        private static Map<String, Object> nestedMaps(int depth) {
            Map<String, Object> value = Map.of("leaf", "value");
            for (int i = 0; i < depth; i++) {
                value = Map.of("level", value);
            }
            return value;
        }

        private static List<Integer> elements(int count) {
            return IntStream.range(0, count).boxed().toList();
        }
    }

    @Nested
    class NothingResolved {

        @Test
        void isNotExported() {
            assertThat(ExportableValue.of(null)).isEmpty();
        }
    }
}
