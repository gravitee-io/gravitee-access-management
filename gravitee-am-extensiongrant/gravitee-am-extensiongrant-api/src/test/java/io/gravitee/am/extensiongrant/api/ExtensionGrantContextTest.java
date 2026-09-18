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
package io.gravitee.am.extensiongrant.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionGrantContextTest {

    @Test
    void shouldTreatMissingResourcesAndScopesAsEmpty() {
        ExtensionGrantContext context = new ExtensionGrantContext("https://as.example.com/domain/oidc", null, null);

        assertEquals(Set.of(), context.requestedResources());
        assertEquals(Set.of(), context.applicationScopes());
    }

    @Test
    void shouldNotFollowLaterChangesToTheGivenSets() {
        Set<String> resources = new HashSet<>(Set.of("https://mcp.example.com"));
        Set<String> scopes = new HashSet<>(Set.of("calendar.read"));

        ExtensionGrantContext context = new ExtensionGrantContext("https://as.example.com/domain/oidc", resources, scopes);
        resources.clear();
        scopes.clear();

        assertEquals(Set.of("https://mcp.example.com"), context.requestedResources());
        assertEquals(Set.of("calendar.read"), context.applicationScopes());
        assertThrows(UnsupportedOperationException.class, () -> context.applicationScopes().add("calendar.write"));
    }
}
