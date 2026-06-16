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

import io.gravitee.am.service.exception.InvalidParameterException;

import java.util.regex.Pattern;

/**
 * A parsed reference to an Automation-API resource, resolved from a path parameter, a request body
 * {@code key} field, or an embedded cross-resource reference. There are two addressing modes:
 * <ul>
 *   <li>{@link KeyRef} — the human, automation-owned {@code key}. Resolution derives the internal id
 *       and is gated on {@code managedBy == AUTOMATION_API}.</li>
 *   <li>{@link IdRef} — the {@code id:<internalUuid>} prefix. Resolution looks the resource up
 *       directly by its internal id and bypasses the {@code managedBy} gate, so a
 *       preexisting ("brownfield") resource the Automation API did not create can still be read,
 *       updated and deleted. Id addressing is update-only — there is no create-by-id.</li>
 * </ul>
 * The {@code key} pattern is enforced here (and only here).
 *
 * @author GraviteeSource Team
 */
public sealed interface AutomationRef permits AutomationRef.KeyRef, AutomationRef.IdRef {

    String ID_PREFIX = "id:";
    int MAX_KEY_LENGTH = 255;
    Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    String KEY_PATTERN_MESSAGE =
            "key must be lowercase alphanumeric and hyphens, starting and ending with an alphanumeric character";

    /** The original token as received, including the {@code id:} prefix for an {@link IdRef}. */
    String raw();

    default boolean isId() {
        return this instanceof IdRef;
    }

    default boolean isKey() {
        return this instanceof KeyRef;
    }

    record KeyRef(String key) implements AutomationRef {
        @Override
        public String raw() {
            return key;
        }
    }

    record IdRef(String id) implements AutomationRef {
        @Override
        public String raw() {
            return ID_PREFIX + id;
        }
    }

    /**
     * Parse a reference token. A token prefixed with {@code id:} is an {@link IdRef} (the remainder is
     * used verbatim). Any other token is a {@link KeyRef} and must satisfy the automation key pattern.
     *
     * @throws InvalidParameterException if the token is blank, an {@code id:} reference is missing its
     *                                   identifier, or a key violates the pattern / length bound
     */
    static AutomationRef parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidParameterException("A resource reference is required");
        }
        if (token.startsWith(ID_PREFIX)) {
            String id = token.substring(ID_PREFIX.length());
            if (id.isBlank()) {
                throw new InvalidParameterException("An '" + ID_PREFIX + "' reference must include an identifier");
            }
            return new IdRef(id);
        }
        if (token.length() > MAX_KEY_LENGTH || !KEY_PATTERN.matcher(token).matches()) {
            throw new InvalidParameterException(KEY_PATTERN_MESSAGE);
        }
        return new KeyRef(token);
    }
}
