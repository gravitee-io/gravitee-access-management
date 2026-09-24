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
import org.json.JSONArray;
import org.json.JSONObject;

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

    @Override
    public String validate(String schema, String json) {
        JSONObject schemaJson = new JSONObject(schema);
        Schema validator = SchemaLoader.builder()
                .schemaJson(schemaJson)
                .draftV7Support()
                .build()
                .load()
                .build();
        JSONObject configuration = new JSONObject(json);
        unsetDeclaredNulls(schemaJson, configuration);
        try {
            validator.validate(configuration);
        } catch (ValidationException e) {
            throw new InvalidJsonException(String.join(", ", e.getAllMessages()));
        }
        return json;
    }

    private static void unsetDeclaredNulls(JSONObject schema, Object value) {
        if (value instanceof JSONObject object) {
            JSONObject declared = schema.optJSONObject("properties");
            if (declared == null) {
                return;
            }
            for (String key : declared.keySet()) {
                if (!object.has(key)) {
                    continue;
                }
                if (JSONObject.NULL.equals(object.get(key))) {
                    object.remove(key);
                } else if (declared.optJSONObject(key) != null) {
                    unsetDeclaredNulls(declared.getJSONObject(key), object.get(key));
                }
            }
        } else if (value instanceof JSONArray array && schema.optJSONObject("items") != null) {
            array.forEach(item -> unsetDeclaredNulls(schema.getJSONObject("items"), item));
        }
    }
}
