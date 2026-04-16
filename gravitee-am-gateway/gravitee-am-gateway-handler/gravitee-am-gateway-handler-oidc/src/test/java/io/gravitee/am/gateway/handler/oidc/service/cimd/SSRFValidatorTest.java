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
package io.gravitee.am.gateway.handler.oidc.service.cimd;

import io.gravitee.am.model.oidc.CIMDSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SSRFValidatorTest {

    private SSRFValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SSRFValidator();
    }

    @Test
    void should_accept_https_uri() {
        CIMDSettings settings = new CIMDSettings();
        assertDoesNotThrow(() -> validator.validate(URI.create("https://example.com/.well-known/oauth-client"), settings));
    }

    @Test
    void should_reject_http_when_not_allowed() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowUnsecuredHttpUri(false);
        CIMDException ex = assertThrows(CIMDException.class,
                () -> validator.validate(URI.create("http://example.com/metadata"), settings));
        assertTrue(ex.getMessage().contains("must use https"));
    }

    @Test
    void should_accept_http_when_allowed() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowUnsecuredHttpUri(true);
        settings.setAllowPrivateIpAddress(true);
        assertDoesNotThrow(() -> validator.validate(URI.create("http://example.com/metadata"), settings));
    }

    @Test
    void should_reject_ftp_scheme() {
        CIMDSettings settings = new CIMDSettings();
        CIMDException ex = assertThrows(CIMDException.class,
                () -> validator.validate(URI.create("ftp://example.com/metadata"), settings));
        assertTrue(ex.getMessage().contains("http or https"));
    }

    @Test
    void should_reject_host_not_in_whitelist() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowedDomains(List.of("trusted.io", "*.example.com"));
        settings.setAllowPrivateIpAddress(true);
        CIMDException ex = assertThrows(CIMDException.class,
                () -> validator.validate(URI.create("https://evil.io/metadata"), settings));
        assertTrue(ex.getMessage().contains("not in the allowed domains"));
    }

    @Test
    void should_accept_host_in_whitelist() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowedDomains(List.of("trusted.io"));
        settings.setAllowPrivateIpAddress(true);
        assertDoesNotThrow(() -> validator.validate(URI.create("https://trusted.io/metadata"), settings));
    }

    @Test
    void should_accept_wildcard_subdomain_match() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowedDomains(List.of("*.example.com"));
        settings.setAllowPrivateIpAddress(true);
        assertDoesNotThrow(() -> validator.validate(URI.create("https://agent1.example.com/metadata"), settings));
    }

    @Test
    void should_reject_wildcard_no_subdomain() {
        // *.example.com should not match "example.com" itself
        assertFalse(SSRFValidator.matchesDomain("example.com", "*.example.com"));
    }

    @Test
    void should_reject_localhost_when_private_not_allowed() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowPrivateIpAddress(false);
        CIMDException ex = assertThrows(CIMDException.class,
                () -> validator.validate(URI.create("https://localhost/metadata"), settings));
        assertTrue(ex.getMessage().contains("private/loopback"));
    }

    @Test
    void should_accept_localhost_when_private_allowed() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowPrivateIpAddress(true);
        assertDoesNotThrow(() -> validator.validate(URI.create("https://localhost/metadata"), settings));
    }
}
