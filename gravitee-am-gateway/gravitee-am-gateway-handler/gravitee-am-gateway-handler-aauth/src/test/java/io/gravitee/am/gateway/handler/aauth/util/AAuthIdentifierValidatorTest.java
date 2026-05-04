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
package io.gravitee.am.gateway.handler.aauth.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AAuthIdentifierValidatorTest {

    // Server identifiers

    @Test
    public void shouldAcceptValidServerIdentifier() {
        assertTrue(AAuthIdentifierValidator.isValidServerIdentifier("https://agent.example"));
    }

    @Test
    public void shouldRejectHttpScheme() {
        assertFalse(AAuthIdentifierValidator.isValidServerIdentifier("http://agent.example"));
    }

    @Test
    public void shouldRejectIdentifierWithPort() {
        assertFalse(AAuthIdentifierValidator.isValidServerIdentifier("https://agent.example:8443"));
    }

    @Test
    public void shouldRejectIdentifierWithPath() {
        assertFalse(AAuthIdentifierValidator.isValidServerIdentifier("https://agent.example/v1"));
    }

    @Test
    public void shouldRejectIdentifierWithTrailingSlash() {
        assertFalse(AAuthIdentifierValidator.isValidServerIdentifier("https://agent.example/"));
    }

    @Test
    public void shouldRejectIdentifierWithUppercase() {
        assertFalse(AAuthIdentifierValidator.isValidServerIdentifier("https://Agent.Example"));
    }

    // Agent identifiers

    @Test
    public void shouldAcceptValidAgentIdentifier() {
        assertTrue(AAuthIdentifierValidator.isValidAgentIdentifier("aauth:assistant-v2@agent.example"));
    }

    @Test
    public void shouldAcceptAgentIdentifierWithPlusAndDot() {
        assertTrue(AAuthIdentifierValidator.isValidAgentIdentifier("aauth:cli+instance.1@tools.example"));
    }

    @Test
    public void shouldRejectAgentIdentifierWithoutPrefix() {
        assertFalse(AAuthIdentifierValidator.isValidAgentIdentifier("assistant@agent.example"));
    }

    @Test
    public void shouldRejectAgentIdentifierWithUppercase() {
        assertFalse(AAuthIdentifierValidator.isValidAgentIdentifier("aauth:My_Agent@agent.example"));
    }

    @Test
    public void shouldRejectAgentIdentifierWithEmptyLocal() {
        assertFalse(AAuthIdentifierValidator.isValidAgentIdentifier("aauth:@agent.example"));
    }

    // Endpoint URLs

    @Test
    public void shouldAcceptValidEndpointUrl() {
        assertTrue(AAuthIdentifierValidator.isValidEndpointUrl("https://ps.example/token"));
    }

    @Test
    public void shouldRejectEndpointUrlWithFragment() {
        assertFalse(AAuthIdentifierValidator.isValidEndpointUrl("https://ps.example/token#frag"));
    }

    @Test
    public void shouldRejectEndpointUrlWithHttpScheme() {
        assertFalse(AAuthIdentifierValidator.isValidEndpointUrl("http://ps.example/token"));
    }

    // agent_server URL (bootstrap §6.2)

    @Test
    public void shouldAcceptHttpsAgentServerUrl() {
        assertNull(AAuthIdentifierValidator.validateAgentServerUrl("https://agent.example", false));
    }

    @Test
    public void shouldAcceptHttpsAgentServerUrlWithPort() {
        assertNull(AAuthIdentifierValidator.validateAgentServerUrl("https://agent.example:8443", false));
    }

    @Test
    public void shouldAcceptHttpsAgentServerUrlWithPath() {
        // bootstrap spec doesn't restrict path; metadata fetch concatenates the well-known suffix.
        assertNull(AAuthIdentifierValidator.validateAgentServerUrl("https://agent.example/v1", false));
    }

    @Test
    public void shouldRejectHttpAgentServerUrlByDefault() {
        String reason = AAuthIdentifierValidator.validateAgentServerUrl("http://agent.example", false);
        assertNotNull(reason);
        assertTrue(reason.contains("https"));
    }

    @Test
    public void shouldAcceptHttpAgentServerUrlWhenInsecureAllowed() {
        assertNull(AAuthIdentifierValidator.validateAgentServerUrl("http://localhost:8080", true));
    }

    @Test
    public void shouldRejectFtpAgentServerUrlEvenWhenInsecureAllowed() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("ftp://agent.example", true));
    }

    @Test
    public void shouldRejectAgentServerUrlWithQuery() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("https://agent.example?x=y", false));
    }

    @Test
    public void shouldRejectAgentServerUrlWithFragment() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("https://agent.example#frag", false));
    }

    @Test
    public void shouldRejectAgentServerUrlWithUserInfo() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("https://user:pass@agent.example", false));
    }

    @Test
    public void shouldRejectNullAgentServerUrl() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl(null, false));
    }

    @Test
    public void shouldRejectBlankAgentServerUrl() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("   ", false));
    }

    @Test
    public void shouldRejectMalformedAgentServerUrl() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("not a url", false));
    }

    @Test
    public void shouldRejectAgentServerUrlWithoutScheme() {
        assertNotNull(AAuthIdentifierValidator.validateAgentServerUrl("agent.example", false));
    }
}
