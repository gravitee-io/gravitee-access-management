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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class RequiredPayloadFieldsTest {

    @Test
    void shouldPassWhenAllFieldsPresent() {
        Optional<String> error = RequiredPayloadFields.forType("Organization")
                .string("id", "id-1")
                .string("name", "name")
                .stringList("hrids", List.of("hrid-1"))
                .validate();

        assertTrue(error.isEmpty());
    }

    @Test
    void shouldRejectNullEmptyAndBlankStrings() {
        assertFalse(RequiredPayloadFields.forType("X").string("id", null).validate().isEmpty());
        assertFalse(RequiredPayloadFields.forType("X").string("id", "").validate().isEmpty());
        assertFalse(RequiredPayloadFields.forType("X").string("id", "   ").validate().isEmpty());
    }

    @Test
    void shouldRejectNullEmptyOrAllBlankStringList() {
        assertFalse(RequiredPayloadFields.forType("X").stringList("hrids", null).validate().isEmpty());
        assertFalse(RequiredPayloadFields.forType("X").stringList("hrids", Collections.emptyList()).validate().isEmpty());
        assertFalse(RequiredPayloadFields.forType("X").stringList("hrids", Arrays.asList(null, "  ")).validate().isEmpty());
    }

    @Test
    void shouldAcceptStringListWithAtLeastOneNonBlankValue() {
        assertTrue(RequiredPayloadFields.forType("X").stringList("hrids", Arrays.asList(null, "hrid-1")).validate().isEmpty());
    }

    @Test
    void shouldListEveryMissingFieldInMessage() {
        Optional<String> error = RequiredPayloadFields.forType("Organization")
                .string("id", null)
                .string("name", "name")
                .stringList("hrids", Collections.emptyList())
                .validate();

        assertTrue(error.isPresent());
        assertEquals("Organization command rejected due to missing or blank required field(s): [id, hrids]", error.get());
    }
}
