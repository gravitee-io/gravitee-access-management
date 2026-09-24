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
package io.gravitee.am.plugins.handlers.api.core;

import io.gravitee.json.validation.InvalidJsonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictJsonSchemaValidatorTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "host": { "type": "string" },
                "port": { "type": "number", "default": 27017, "maximum": 65535 }
              },
              "required": ["host", "port"],
              "additionalProperties": false
            }
            """;

    private final StrictJsonSchemaValidator validator = new StrictJsonSchemaValidator();

    @Test
    void shouldReturnAValidConfigurationUnchanged() {
        String json = "{\"host\":\"mongo\",\"port\":27017}";

        assertEquals(json, validator.validate(SCHEMA, json));
    }

    @Test
    void shouldRejectAnUndeclaredPropertyInsteadOfDroppingIt() {
        InvalidJsonException ex = assertThrows(InvalidJsonException.class,
                () -> validator.validate(SCHEMA, "{\"host\":\"mongo\",\"port\":27017,\"bogus\":\"x\"}"));

        assertTrue(ex.getMessage().contains("bogus"), ex.getMessage());
    }

    @Test
    void shouldRejectAMissingRequiredPropertyInsteadOfDefaultingIt() {
        InvalidJsonException ex = assertThrows(InvalidJsonException.class,
                () -> validator.validate(SCHEMA, "{\"host\":\"mongo\"}"));

        assertTrue(ex.getMessage().contains("port"), ex.getMessage());
    }

    @Test
    void shouldRejectANullValueInsteadOfClearingIt() {
        InvalidJsonException ex = assertThrows(InvalidJsonException.class,
                () -> validator.validate(SCHEMA, "{\"host\":\"mongo\",\"port\":null}"));

        assertTrue(ex.getMessage().contains("port"), ex.getMessage());
    }

    @Test
    void shouldReportEveryViolation() {
        InvalidJsonException ex = assertThrows(InvalidJsonException.class,
                () -> validator.validate(SCHEMA, "{\"host\":\"mongo\",\"port\":99999,\"bogus\":\"x\"}"));

        assertTrue(ex.getMessage().contains("bogus") && ex.getMessage().contains("99999"), ex.getMessage());
    }
}
