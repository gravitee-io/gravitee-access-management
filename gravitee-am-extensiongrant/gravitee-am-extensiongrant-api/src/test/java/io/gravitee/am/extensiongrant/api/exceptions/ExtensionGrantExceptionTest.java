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
package io.gravitee.am.extensiongrant.api.exceptions;

import io.gravitee.am.extensiongrant.api.ExtensionGrantResult;
import io.gravitee.am.identityprovider.api.DefaultUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionGrantExceptionTest {

    private static final ExtensionGrantResult VERIFIED = new ExtensionGrantResult(new DefaultUser("alice"), "idp-id",
            Map.of("iss", "https://idp.example.com"), List.of(), "https://mcp.example.com", Set.of("calendar.read"));

    @Test
    void shouldCarryTheResultVerifiedBeforeAGrantRefusal() {
        ExtensionGrantException refusal = new InvalidGrantException("Assertion audience does not include this domain", VERIFIED);

        assertEquals("Assertion audience does not include this domain", refusal.getMessage());
        assertSame(VERIFIED, refusal.getVerifiedResult().orElseThrow());
    }

    @Test
    void shouldCarryTheResultVerifiedBeforeAResourceRefusal() {
        ExtensionGrantException refusal = new InvalidResourceException("Request must name a single resource", VERIFIED);

        assertEquals("Request must name a single resource", refusal.getMessage());
        assertSame(VERIFIED, refusal.getVerifiedResult().orElseThrow());
    }

    @Test
    void shouldCarryTheResultVerifiedBeforeAScopeRefusal() {
        ExtensionGrantException refusal = new InvalidScopeException("Assertion scope claim must be a space-delimited string", VERIFIED);

        assertEquals("Assertion scope claim must be a space-delimited string", refusal.getMessage());
        assertSame(VERIFIED, refusal.getVerifiedResult().orElseThrow());
    }

    @Test
    void shouldCarryNoVerifiedResultByDefault() {
        assertTrue(new InvalidGrantException("Assertion cannot be parsed").getVerifiedResult().isEmpty());
        assertTrue(new InvalidGrantException("Assertion verification failed", new IllegalStateException()).getVerifiedResult().isEmpty());
        assertTrue(new InvalidResourceException("Request must name a single resource").getVerifiedResult().isEmpty());
        assertTrue(new InvalidScopeException("Assertion scope claim must be a space-delimited string").getVerifiedResult().isEmpty());
    }
}
