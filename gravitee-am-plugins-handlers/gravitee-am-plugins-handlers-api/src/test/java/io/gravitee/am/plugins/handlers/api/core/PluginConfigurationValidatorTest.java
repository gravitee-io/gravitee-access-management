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

import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator.Result;
import io.gravitee.json.validation.InvalidJsonException;
import io.gravitee.json.validation.JsonSchemaValidator;
import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationValidatorTest {


    @Test
    public void if_validation_passes_return_valid_response(){
        JsonSchemaValidator schemaValidator = Mockito.mock(JsonSchemaValidator.class);
        PluginConfigurationValidator validator = new PluginConfigurationValidator("id", "schema", schemaValidator);
        String data = "data";

        Mockito.when(schemaValidator.validate("schema", "data")).thenReturn("valid");

        Result result = validator.validate(data);

        Assertions.assertTrue(result.isValid());
        Assertions.assertEquals("", result.getMsg());
    }

    @Test
    public void if_validation_throws_ex_return_invalid_response(){
        JsonSchemaValidator schemaValidator = Mockito.mock(JsonSchemaValidator.class);
        PluginConfigurationValidator validator = new PluginConfigurationValidator("id", "schema", schemaValidator);
        String data = "data";

        Mockito.when(schemaValidator.validate("schema", "data")).thenThrow(new InvalidJsonException("message"));

        Result result = validator.validate(data);

        Assertions.assertFalse(result.isValid());
        Assertions.assertEquals("message", result.getMsg());
    }

}