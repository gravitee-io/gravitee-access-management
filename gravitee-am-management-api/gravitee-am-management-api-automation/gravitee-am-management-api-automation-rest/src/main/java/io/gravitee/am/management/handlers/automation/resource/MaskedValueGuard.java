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
package io.gravitee.am.management.handlers.automation.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.reactivex.rxjava3.core.Completable;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.management.service.AbstractSensitiveProxy.SENSITIVE_VALUE;
import static io.gravitee.am.management.service.AbstractSensitiveProxy.SENSITIVE_VALUE_PATTERN;

/**
 * Rejects, on create, a plugin configuration that sends the mask ({@code ********}) in a sensitive field.
 * A field is sensitive when the resource's service proxy masks it, so callers pass both the submitted and
 * the masked configuration.
 *
 * @author GraviteeSource Team
 */
final class MaskedValueGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MaskedValueGuard() {
    }

    static Completable rejectMaskedSensitiveValues(String submittedConfiguration, String maskedConfiguration) {
        return Completable.defer(() -> findMaskedSensitiveValue(submittedConfiguration, maskedConfiguration)
                .map(path -> Completable.error(new InvalidParameterException("Field 'configuration" + path
                        + "' holds the masked value '" + SENSITIVE_VALUE + "'; supply the secret itself. A masked "
                        + "value only keeps an existing secret when updating")))
                .orElseGet(Completable::complete));
    }

    /**
     * @return the JSON pointer of the first sensitive field sent masked, if any
     */
    static Optional<String> findMaskedSensitiveValue(String submittedConfiguration, String maskedConfiguration) {
        try {
            return find(OBJECT_MAPPER.readTree(submittedConfiguration), OBJECT_MAPPER.readTree(maskedConfiguration), "");
        } catch (JsonProcessingException e) {
            // Plugin validation rejects malformed JSON.
            return Optional.empty();
        }
    }

    private static Optional<String> find(JsonNode submitted, JsonNode masked, String path) {
        if (submitted == null || masked == null) {
            return Optional.empty();
        }
        if (submitted.isTextual()) {
            return SENSITIVE_VALUE_PATTERN.matcher(submitted.asText().trim()).matches()
                    && masked.isTextual() && SENSITIVE_VALUE.equals(masked.asText())
                    ? Optional.of(path) : Optional.empty();
        }
        if (submitted.isArray()) {
            for (int i = 0; i < submitted.size(); i++) {
                Optional<String> found = find(submitted.get(i), masked.get(i), path + "/" + i);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (submitted.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = submitted.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                Optional<String> found = find(field.getValue(), masked.get(field.getKey()),
                        path + "/" + field.getKey().replace("~", "~0").replace("/", "~1"));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
