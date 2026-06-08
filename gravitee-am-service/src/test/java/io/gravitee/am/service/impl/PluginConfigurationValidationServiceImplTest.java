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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.json.validation.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class PluginConfigurationValidationServiceImplTest {

    private static final String TYPE = "some-am-idp";

    private PluginConfigurationValidatorsRegistry registry;
    private PluginConfigurationValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        registry = new PluginConfigurationValidatorsRegistry();
        service = new PluginConfigurationValidationServiceImpl(registry, new ObjectMapper());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void should_reject_null_or_blank_configuration(String configuration) {
        var ex = assertThrows(InvalidPluginConfigurationException.class, () -> service.validate(TYPE, configuration));
        assertEquals("configuration is required", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "not-json", "}{", "{\"a\":}"})
    void should_reject_malformed_json_configuration(String configuration) {
        var ex = assertThrows(InvalidPluginConfigurationException.class, () -> service.validate(TYPE, configuration));
        assertEquals("configuration is not valid JSON", ex.getMessage());
    }

    @Test
    void should_pass_when_well_formed_json_and_no_validator_registered() {
        assertDoesNotThrow(() -> service.validate("unregistered-type", "{\"a\":1}"));
    }

    @Test
    void should_pass_when_well_formed_json_matches_schema() {
        var jsonSchemaValidator = mock(JsonSchemaValidator.class);
        when(jsonSchemaValidator.validate(anyString(), anyString())).thenReturn("");
        registry.put(new PluginConfigurationValidator(TYPE, "schema", jsonSchemaValidator));

        assertDoesNotThrow(() -> service.validate(TYPE, "{\"a\":1}"));
    }

    @Test
    void should_reject_when_well_formed_json_fails_schema() {
        var jsonSchemaValidator = mock(JsonSchemaValidator.class);
        when(jsonSchemaValidator.validate(anyString(), anyString())).thenThrow(new RuntimeException("missing required field"));
        registry.put(new PluginConfigurationValidator(TYPE, "schema", jsonSchemaValidator));

        var ex = assertThrows(InvalidPluginConfigurationException.class, () -> service.validate(TYPE, "{\"a\":1}"));
        assertEquals("missing required field", ex.getMessage());
    }
}
