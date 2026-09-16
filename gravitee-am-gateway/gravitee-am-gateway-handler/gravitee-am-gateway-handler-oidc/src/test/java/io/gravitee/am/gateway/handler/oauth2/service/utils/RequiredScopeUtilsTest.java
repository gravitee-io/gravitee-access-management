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
package io.gravitee.am.gateway.handler.oauth2.service.utils;

import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredScopeUtilsTest {

    private Client client;

    @BeforeEach
    void setUp() {
        ApplicationScopeSettings admin = new ApplicationScopeSettings("admin");
        admin.setRequiredScope(true);
        client = new Client();
        client.setScopeSettings(List.of(admin, new ApplicationScopeSettings("read"), new ApplicationScopeSettings("write")));
    }

    @Test
    void requiredScopeKeys_returnsOnlyRequiredScopes() {
        assertEquals(Set.of("admin"), RequiredScopeUtils.requiredScopeKeys(client));
    }

    @Test
    void requiredScopeKeys_isEmptyWithoutClientOrSettings() {
        assertTrue(RequiredScopeUtils.requiredScopeKeys(null).isEmpty());
        assertTrue(RequiredScopeUtils.requiredScopeKeys(new Client()).isEmpty());
    }

    @Test
    void canApproveWithoutSelection_whenRequiredScopesAreAlreadyApproved() {
        assertTrue(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("admin", "read", "write"), Set.of("admin")));
    }

    @Test
    void canApproveWithoutSelection_whenNoRequiredScopeIsRequestedAndSomethingIsApproved() {
        assertTrue(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("read", "write"), Set.of("read")));
    }

    @Test
    void cannotApproveWithoutSelection_whenRequiredScopeIsPending() {
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("admin", "read", "write"), Set.of("read")));
    }

    @Test
    void cannotApproveWithoutSelection_whenNothingRequestedIsApproved() {
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("read", "write"), Set.of()));
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("read", "write"), Set.of("other")));
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(new Client(), Set.of("read"), Set.of()));
    }

    @Test
    void cannotApproveWithoutSelection_withMissingInputs() {
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, null, Set.of("read")));
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of("read"), null));
        assertFalse(RequiredScopeUtils.canApproveWithoutSelection(client, Set.of(), Set.of("read")));
    }
}
