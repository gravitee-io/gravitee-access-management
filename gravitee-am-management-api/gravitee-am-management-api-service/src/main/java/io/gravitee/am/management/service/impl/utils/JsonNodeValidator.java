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
import com.fasterxml.jackson.databind.node.NumericNode;

/**
 * Validates the values of {@link JsonNode}s.
 */
public class JsonNodeValidator {

    private JsonNodeValidator(){}

    /**
     * Validates a JSON document (in the form of a {@link JsonNode}) for 'correctness' of values.
     * This is currently restricted to ensuring numeric values are not negative; future enhancement might involve using regex
     * or similar to validate specific string values.
     *
     * @param idpConfig {@link JsonNode} representing a JSON document.
     */
    public static void validateConfiguration(JsonNode idpConfig) {
        idpConfig.elements().forEachRemaining(jsonNode -> {
            if (jsonNode instanceof NumericNode) {
                boolean isNegative = switch (jsonNode.numberType()) {
                    case INT, LONG, BIG_INTEGER -> jsonNode.asInt() < 0;
                    case FLOAT, DOUBLE, BIG_DECIMAL -> jsonNode.asDouble() < 0;
                };
                if (isNegative) {
                    throw new IllegalArgumentException("Negative numbers not allowed");
                }
            }
        });
    }
}
