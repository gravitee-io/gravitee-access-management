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
import io.gravitee.json.validation.JsonSchemaValidator;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;

/**
 * Reports every violation of the schema. {@link io.gravitee.json.validation.JsonSchemaValidatorImpl} instead drops a property the
 * schema does not declare, fills a missing required one with its default and clears null values, so a
 * misspelled key passes and the consumer silently falls back to its own default.
 *
 * @author GraviteeSource Team
 */
public class StrictJsonSchemaValidator implements JsonSchemaValidator {

    @Override
    public String validate(String schema, String json) {
        Schema validator = SchemaLoader.builder()
                .schemaJson(new JSONObject(schema))
                .draftV7Support()
                .build()
                .load()
                .build();
        try {
            validator.validate(new JSONObject(json));
        } catch (ValidationException e) {
            throw new InvalidJsonException(String.join(", ", e.getAllMessages()));
        }
        return json;
    }
}
