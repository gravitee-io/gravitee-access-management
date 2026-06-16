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

import io.gravitee.am.management.handlers.automation.resource.AutomationRef.IdRef;
import io.gravitee.am.management.handlers.automation.resource.AutomationRef.KeyRef;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class AutomationRefTest {

    @Test
    void parses_a_plain_token_as_a_key() {
        AutomationRef ref = AutomationRef.parse("corporate-ldap");
        assertInstanceOf(KeyRef.class, ref);
        assertTrue(ref.isKey());
        assertEquals("corporate-ldap", ref.raw());
    }

    @Test
    void parses_an_id_prefixed_token_as_an_id() {
        AutomationRef ref = AutomationRef.parse("id:94157683-f481-45a9-9576-83f48145a9a0");
        assertInstanceOf(IdRef.class, ref);
        assertTrue(ref.isId());
        assertEquals("94157683-f481-45a9-9576-83f48145a9a0", ((IdRef) ref).id());
        // raw() preserves the prefix so error messages and stored references round-trip
        assertEquals("id:94157683-f481-45a9-9576-83f48145a9a0", ref.raw());
    }

    @Test
    void does_not_apply_the_key_pattern_to_an_id_reference() {
        // a system identity provider's id is default-idp-<uuid> — not a bare UUID, and not a valid key
        AutomationRef ref = AutomationRef.parse("id:default-idp-94157683-f481-45a9-9576-83f48145a9a0");
        assertInstanceOf(IdRef.class, ref);
        assertEquals("default-idp-94157683-f481-45a9-9576-83f48145a9a0", ((IdRef) ref).id());
    }

    @Test
    void rejects_a_null_or_blank_token() {
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse(null));
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse("  "));
    }

    @Test
    void rejects_an_id_reference_with_no_identifier() {
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse("id:"));
    }

    @Test
    void rejects_a_key_violating_the_pattern() {
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse("Has-Caps"));
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse("-leading-hyphen"));
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse("trailing-hyphen-"));
    }

    @Test
    void rejects_a_key_exceeding_the_length_bound() {
        String tooLong = "a".repeat(AutomationRef.MAX_KEY_LENGTH + 1);
        assertThrows(InvalidParameterException.class, () -> AutomationRef.parse(tooLong));
    }
}
