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
package io.gravitee.am.management.service.impl.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class JsonNodeValidatorTest {

    @ParameterizedTest
    @MethodSource("negatives")
    void shouldThrowIllegalArgForNegativeNumbers(JsonNode negativeNumericNode) {
        var jsonNode = mock(JsonNode.class);
        given(jsonNode.elements()).willReturn(List.of(negativeNumericNode).iterator());
        assertThrows(IllegalArgumentException.class, () -> JsonNodeValidator.validateConfiguration(jsonNode));
    }

    @ParameterizedTest
    @MethodSource("positives")
    void shouldNotThrowForPositiveNumbers(JsonNode positiveNumericNode) {
        var jsonNode = mock(JsonNode.class);
        given(jsonNode.elements()).willReturn(List.of(positiveNumericNode).iterator());
        assertDoesNotThrow(() -> JsonNodeValidator.validateConfiguration(jsonNode));
    }

    static Stream<JsonNode> negatives() {
        return Stream.of(
                new IntNode(-1),
                new LongNode(-15555555L),
                new FloatNode(-1.8F),
                new DoubleNode(-991.10));
    }

    static Stream<JsonNode> positives() {
        return Stream.of(
                new IntNode(1),
                new LongNode(15555555L),
                new FloatNode(1.8F),
                new DoubleNode(991.10));
    }
}