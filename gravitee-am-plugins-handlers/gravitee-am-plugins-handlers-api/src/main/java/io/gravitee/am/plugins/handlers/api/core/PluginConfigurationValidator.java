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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PluginConfigurationValidator {
    @Getter
    private final String pluginIdentifier;
    private final String schema;
    private final JsonSchemaValidator jsonSchemaValidator;

    public Result validate(String pluginData) {
        try {
            jsonSchemaValidator.validate(schema, pluginData);
            return Result.VALID_RESPONSE;
        } catch (InvalidJsonException e) {
            return new Result(false, e.getMessage());
        }
    }

    @RequiredArgsConstructor
    public static final class Result {
        private static final Result VALID_RESPONSE = new Result(true, "");
        private final Boolean valid;
        private final String msg;

        public boolean isValid() {
            return valid;
        }

        public String getMsg(){
            return msg;
        }

    }
}
