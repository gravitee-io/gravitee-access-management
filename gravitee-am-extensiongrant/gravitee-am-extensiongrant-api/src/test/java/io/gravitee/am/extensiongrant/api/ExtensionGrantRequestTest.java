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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionGrantRequestTest {

    private static final String ISSUER = "https://as.example.com/domain/oidc";

    @Test
    void shouldTreatMissingScopesResourcesAndParametersAsEmpty() {
        ExtensionGrantRequest request = ExtensionGrantRequest.builder().clientId("client-id").authorizationServerIssuer(ISSUER).build();

        assertEquals(Set.of(), request.getRequestedScopes());
        assertEquals(Map.of(), request.getParameters());
        assertEquals(Set.of(), request.getRequestedResources());
        assertEquals(Set.of(), request.getApplicationScopes());
        assertNull(request.parameter("assertion"));
    }

    @Test
    void shouldNotFollowLaterChangesToTheGivenCollections() {
        Set<String> resources = new HashSet<>(Set.of("https://mcp.example.com"));
        Set<String> scopes = new HashSet<>(Set.of("calendar.read"));
        Map<String, String> parameters = new HashMap<>(Map.of("assertion", "token"));

        ExtensionGrantRequest request = ExtensionGrantRequest.builder()
                .clientId("client-id")
                .parameters(parameters)
                .authorizationServerIssuer(ISSUER)
                .requestedResources(resources)
                .applicationScopes(scopes)
                .build();
        resources.clear();
        scopes.clear();
        parameters.clear();

        assertEquals(Set.of("https://mcp.example.com"), request.getRequestedResources());
        assertEquals(Set.of("calendar.read"), request.getApplicationScopes());
        assertEquals("token", request.parameter("assertion"));
        assertThrows(UnsupportedOperationException.class, () -> request.getApplicationScopes().add("calendar.write"));
    }
}
