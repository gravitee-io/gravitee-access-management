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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Draft7;
import com.networknt.schema.keyword.AnnotationKeyword;
import io.gravitee.json.validation.InvalidJsonException;
import io.gravitee.json.validation.JsonSchemaValidator;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Reports every violation of the schema. {@link io.gravitee.json.validation.JsonSchemaValidatorImpl} instead drops a property the
 * schema does not declare and fills a missing required one with its default, so a misspelled key passes and
 * the consumer silently falls back to its own default.
 * <p>
 * A null value stands for an unset property, so it is accepted for a property the schema declares, but a
 * misspelled key is still reported whatever its value.
 *
 * @author GraviteeSource Team
 */
public class StrictJsonSchemaValidator implements JsonSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // the schema forms of the plugins carry the legacy "id" keyword
    private static final Dialect DRAFT_7_WITH_ID = Dialect.builder(Draft7.getInstance())
            .keyword(new AnnotationKeyword("id"))
            .build();
    // the messages are returned to the caller, they must not depend on the locale of the JVM
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(DRAFT_7_WITH_ID,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build()));

    @Override
    public String validate(String schema, String json) {
        try {
            JsonNode schemaJson = MAPPER.readTree(schema);
            JsonNode configuration = MAPPER.readTree(json);
            unsetDeclaredNulls(schemaJson, configuration);
            List<Error> errors = SCHEMA_REGISTRY.getSchema(schemaJson).validate(configuration);
            if (!errors.isEmpty()) {
                throw new InvalidJsonException(errors.stream().map(Error::toString).collect(Collectors.joining(", ")));
            }
        } catch (JsonProcessingException e) {
            throw new InvalidJsonException(e.getMessage(), e);
        }
        return json;
    }

    private static void unsetDeclaredNulls(JsonNode schema, JsonNode value) {
        if (value instanceof ObjectNode object) {
            JsonNode declared = schema.path("properties");
            if (!declared.isObject()) {
                return;
            }
            declared.fieldNames().forEachRemaining(key -> {
                JsonNode property = object.get(key);
                if (property == null) {
                    return;
                }
                if (property.isNull()) {
                    object.remove(key);
                } else if (declared.get(key).isObject()) {
                    unsetDeclaredNulls(declared.get(key), property);
                }
            });
        } else if (value instanceof ArrayNode array && schema.path("items").isObject()) {
            array.forEach(item -> unsetDeclaredNulls(schema.get("items"), item));
        }
    }
}
