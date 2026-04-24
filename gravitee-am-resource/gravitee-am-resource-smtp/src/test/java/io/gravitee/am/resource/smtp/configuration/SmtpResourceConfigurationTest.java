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
package io.gravitee.am.resource.smtp.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpResourceConfigurationTest {

    @Test
    void should_use_explicit_authentication_flag_when_defined() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setUsername("user");
        configuration.setPassword("password");

        configuration.setAuthentication(Boolean.FALSE);
        assertFalse(configuration.authenticateEnabled());

        configuration.setAuthentication(Boolean.TRUE);
        assertTrue(configuration.authenticateEnabled());
    }

    @Test
    void should_enable_authentication_when_flag_absent_and_credentials_are_provided() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setAuthentication(null);
        configuration.setUsername("user");
        configuration.setPassword("password");

        assertTrue(configuration.authenticateEnabled());
    }

    @Test
    void should_disable_authentication_when_flag_absent_and_credentials_are_missing() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setAuthentication(null);
        configuration.setUsername("user");
        configuration.setPassword(null);

        assertFalse(configuration.authenticateEnabled());
    }

    @Test
    void should_not_detect_oauth2_authentication_when_authentication_is_inferred() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setAuthentication(null);
        configuration.setAuthenticationType(SmtpResourceConfiguration.AUTH_TYPE_OAUTH2);

        assertFalse(configuration.isOauth2Authentication());
        assertFalse(configuration.isBasicAuthentication());
    }

    @Test
    void should_detect_basic_authentication_by_default_when_authentication_type_is_null() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setAuthentication(null);
        configuration.setAuthenticationType(null);
        configuration.setUsername("user");
        configuration.setPassword("password");

        assertTrue(configuration.isBasicAuthentication());
        assertFalse(configuration.isOauth2Authentication());
    }

    @Test
    void should_use_smtp_as_default_protocol() {
        var configuration = new SmtpResourceConfiguration();

        assertEquals("smtp", configuration.getProtocol());
    }
}
