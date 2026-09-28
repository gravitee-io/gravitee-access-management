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
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class MaskedValueGuardTest {

    @Test
    void finds_a_top_level_sensitive_value_sent_masked() {
        Optional<String> path = MaskedValueGuard.findMaskedSensitiveValue(
                "{\"clientId\":\"app\",\"clientSecret\":\"********\"}",
                "{\"clientId\":\"app\",\"clientSecret\":\"********\"}");

        assertEquals(Optional.of("/clientSecret"), path);
    }

    @Test
    void finds_a_nested_sensitive_value_inside_an_array() {
        Optional<String> path = MaskedValueGuard.findMaskedSensitiveValue(
                "{\"users\":[{\"username\":\"alice\",\"password\":\"secret\"},{\"username\":\"bob\",\"password\":\"***\"}]}",
                "{\"users\":[{\"username\":\"alice\",\"password\":\"********\"},{\"username\":\"bob\",\"password\":\"********\"}]}");

        assertEquals(Optional.of("/users/1/password"), path);
    }

    @Test
    void ignores_a_sensitive_value_that_carries_a_real_secret() {
        Optional<String> path = MaskedValueGuard.findMaskedSensitiveValue(
                "{\"clientSecret\":\"s3cr3t\"}",
                "{\"clientSecret\":\"********\"}");

        assertTrue(path.isEmpty());
    }

    @Test
    void ignores_an_asterisk_in_a_field_the_masking_leaves_alone() {
        Optional<String> path = MaskedValueGuard.findMaskedSensitiveValue(
                "{\"scope\":\"*\",\"clientSecret\":\"s3cr3t\"}",
                "{\"scope\":\"*\",\"clientSecret\":\"********\"}");

        assertTrue(path.isEmpty());
    }

    @Test
    void ignores_a_field_the_masking_dropped() {
        Optional<String> path = MaskedValueGuard.findMaskedSensitiveValue(
                "{\"clientSecret\":\"********\"}",
                "{}");

        assertTrue(path.isEmpty());
    }

    @Test
    void rejects_with_the_json_path_of_the_masked_value() {
        MaskedValueGuard.rejectMaskedSensitiveValues(
                        "{\"clientSecret\":\"********\"}",
                        "{\"clientSecret\":\"********\"}")
                .test()
                .assertError(error -> error instanceof InvalidParameterException
                        && error.getMessage().contains("configuration/clientSecret"));
    }

    @Test
    void completes_when_nothing_is_masked() {
        MaskedValueGuard.rejectMaskedSensitiveValues(
                        "{\"clientSecret\":\"s3cr3t\"}",
                        "{\"clientSecret\":\"********\"}")
                .test()
                .assertComplete();
    }
}
