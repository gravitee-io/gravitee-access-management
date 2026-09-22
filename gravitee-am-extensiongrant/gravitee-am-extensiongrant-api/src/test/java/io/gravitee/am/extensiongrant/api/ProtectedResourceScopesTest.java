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

class ProtectedResourceScopesTest {

    @Test
    void shouldTreatMissingScopesAsEmpty() {
        ProtectedResourceScopes resourceScopes = new ProtectedResourceScopes(null, null);

        assertEquals(Set.of(), resourceScopes.scopes());
        assertEquals(Set.of(), resourceScopes.parameterizedScopes());
    }

    @Test
    void shouldNotFollowLaterChangesToTheGivenSets() {
        Set<String> scopes = new HashSet<>(Set.of("calendar", "calendar.read"));
        Set<String> parameterizedScopes = new HashSet<>(Set.of("calendar"));

        ProtectedResourceScopes resourceScopes = new ProtectedResourceScopes(scopes, parameterizedScopes);
        scopes.clear();
        parameterizedScopes.clear();

        assertEquals(Set.of("calendar", "calendar.read"), resourceScopes.scopes());
        assertEquals(Set.of("calendar"), resourceScopes.parameterizedScopes());
    }
}
