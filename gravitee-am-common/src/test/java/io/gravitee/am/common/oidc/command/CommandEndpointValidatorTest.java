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
package io.gravitee.am.common.oidc.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CommandEndpointValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "http://rp.example.com/commands", "https://rp.example.com/commands?tenant=t1"})
    public void shouldAcceptAbsentOrValidEndpoint(String endpoint) {
        assertDoesNotThrow(() -> CommandEndpointValidator.validate(endpoint));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://rp.example.com/commands#fragment", // fragment forbidden
            "/commands", // not absolute
            "https:///commands", // no host
            "not a uri at all ::"
    })
    public void shouldRejectInvalidEndpoint(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> CommandEndpointValidator.validate(endpoint));
    }

    @Test
    public void shouldAcceptUppercaseHttpsScheme() {
        assertDoesNotThrow(() -> CommandEndpointValidator.validate("HTTPS://rp.example.com/commands"));
    }
}
