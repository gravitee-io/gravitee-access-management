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
package io.gravitee.am.management.service.impl.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper to validate and reject cockpit command payloads that are missing fields required to
 * produce a usable entity.
 *
 * @author GraviteeSource Team
 */
final class RequiredPayloadFields {

    private final String commandType;
    private final List<String> missing = new ArrayList<>();

    private RequiredPayloadFields(String commandType) {
        this.commandType = commandType;
    }

    /**
     * Starts a validation chain for the given command type.
     *
     * @param commandType human-readable command type used in the rejection message (e.g. "Organization")
     * @return a new, empty validation chain
     */
    static RequiredPayloadFields forType(String commandType) {
        return new RequiredPayloadFields(commandType);
    }

    /**
     * Requires a non-null, non-blank string value.
     *
     * @param field the field name reported when the value is missing
     * @param value the value to check
     * @return this chain, for fluent use
     */
    RequiredPayloadFields string(String field, String value) {
        if (value == null || value.isBlank()) {
            missing.add(field);
        }
        return this;
    }

    /**
     * Requires a non-null list holding at least one non-blank value.
     *
     * @param field the field name reported when the value is missing
     * @param values the list to check
     * @return this chain, for fluent use
     */
    RequiredPayloadFields stringList(String field, List<String> values) {
        if (values == null || values.stream().allMatch(v -> v == null || v.isBlank())) {
            missing.add(field);
        }
        return this;
    }

    /**
     * Validates every field accumulated in the chain.
     *
     * @return an error message describing the missing fields, or empty if the payload is valid
     */
    Optional<String> validate() {
        return missing.isEmpty()
                ? Optional.empty()
                : Optional.of(commandType + " command rejected due to missing or blank required field(s): " + missing);
    }
}
