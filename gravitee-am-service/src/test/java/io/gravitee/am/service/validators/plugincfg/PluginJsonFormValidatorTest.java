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
package io.gravitee.am.service.validators.plugincfg;

import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.service.CertificateServiceTest;
import io.gravitee.am.service.model.PluginConfigurationPayload;
import io.gravitee.json.validation.JsonSchemaValidator;
import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.util.io.IOUtil;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class PluginJsonFormValidatorTest {

    private PluginConfigurationValidatorsRegistry registry;
    private PluginJsonFormValidator jsonFormValidator;

    private static final String SCHEMA = loadResource("certificate-schema-definition.json");


    @BeforeEach
    public void setUp() {
        registry = new PluginConfigurationValidatorsRegistry();
        jsonFormValidator = new PluginJsonFormValidator(registry);
    }

    @Test
    public void not_registered_schema_validator_should_return_true() throws Exception {
        // given
        var validator = new PluginConfigurationValidator("id", SCHEMA, new JsonSchemaValidatorImpl());
        registry.put(validator);


        var payload = new TestPayload("otherId", "");

        // when
        boolean result = jsonFormValidator.isValid(payload, mock(ConstraintValidatorContext.class));

        // then
        Assertions.assertTrue(result);
    }


    @Test
    public void registered_schema_validator_should_return_true_when_payload_is_valid() throws Exception {
        // given
        var validator = new PluginConfigurationValidator("id", SCHEMA, new JsonSchemaValidatorImpl());
        registry.put(validator);

        var cfg = """
                {
                 "content":"server.p12",
                 "storepass":"server-secret",
                 "alias":"am-server",
                 "keypass":"server-secret"
                }
                """;
        var payload = new TestPayload("id", cfg);

        // when
        boolean result = jsonFormValidator.isValid(payload, mock(ConstraintValidatorContext.class));

        // then
        Assertions.assertTrue(result);
    }

    @Test
    public void registered_schema_validator_should_return_false_when_payload_is_invalid() throws Exception {
        // given
        var validator = new PluginConfigurationValidator("id", SCHEMA, new JsonSchemaValidatorImpl());
        registry.put(validator);

        var cfg = """
                {
                 "content":"server.p12",
                 "storepass":"server-secret",
                 "keypass":"server-secret"
                }
                """;
        var payload = new TestPayload("id", cfg);

        var ctx = mock(ConstraintValidatorContext.class);
        var violation = mock(ConstraintViolationBuilder.class);
        when(ctx.buildConstraintViolationWithTemplate(anyString())).thenReturn(violation);

        // when
        boolean result = jsonFormValidator.isValid(payload, ctx);

        // then
        Assertions.assertFalse(result);
        Mockito.verify(violation, times(1)).addConstraintViolation();
    }

    @Test
    public void should_escape_braces_in_violation_message_to_avoid_HV000149() {
        // given a validator whose failure message contains brace characters (as the JSON parser produces,
        // e.g. "A JSONObject text must begin with '{' at 0"). Passed verbatim, Hibernate would treat the
        // brace as a message parameter and fail interpolation with HV000149.
        var jsonSchemaValidator = mock(JsonSchemaValidator.class);
        when(jsonSchemaValidator.validate(anyString(), anyString()))
                .thenThrow(new RuntimeException("A JSONObject text must begin with '{' at 0 [character 1 line 1]"));
        registry.put(new PluginConfigurationValidator("id", "schema", jsonSchemaValidator));

        var payload = new TestPayload("id", "");

        var ctx = mock(ConstraintValidatorContext.class);
        var violation = mock(ConstraintViolationBuilder.class);
        var template = ArgumentCaptor.forClass(String.class);
        when(ctx.buildConstraintViolationWithTemplate(template.capture())).thenReturn(violation);

        // when
        boolean result = jsonFormValidator.isValid(payload, ctx);

        // then
        Assertions.assertFalse(result);
        String captured = template.getValue();
        Assertions.assertTrue(captured.contains("\\{"), "brace should be escaped: " + captured);
        Assertions.assertFalse(captured.replace("\\{", "").contains("{"), "unescaped '{' present: " + captured);
        Assertions.assertFalse(captured.replace("\\}", "").contains("}"), "unescaped '}' present: " + captured);
    }

    @SneakyThrows
    private static String loadResource(String name) {
        try (InputStream input = CertificateServiceTest.class.getClassLoader().getResourceAsStream(name)) {
            return IOUtil.readLines(input).stream().collect(Collectors.joining());
        }
    }

    @Value
    private static class TestPayload implements PluginConfigurationPayload {
        String type;
        String configuration;
    }
}