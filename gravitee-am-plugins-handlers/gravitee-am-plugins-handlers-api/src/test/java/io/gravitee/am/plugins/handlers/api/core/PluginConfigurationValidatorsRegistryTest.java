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

import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationValidatorsRegistryTest {

    PluginConfigurationValidatorsRegistry registry = new PluginConfigurationValidatorsRegistry();

    @Test
    public void added_plugin_validator_should_be_accessible() {
        // given
        var validator = new PluginConfigurationValidator("id", "schema", new JsonSchemaValidatorImpl());
        registry.put(validator);

        // expect
        Assertions.assertEquals(validator, registry.get("id").get());
    }

    @Test
    public void should_return_null_if_validator_is_not_found() {
        // expect
        Assertions.assertTrue(registry.get("id").isEmpty());
    }

}