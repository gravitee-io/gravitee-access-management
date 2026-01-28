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
package io.gravitee.am.service.spring.email;

import io.gravitee.common.util.EnvironmentUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class EmailPropertiesLoaderTest {

    @Test
    void shouldLowercaseUppercaseKeysOnly() {
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        Map<String, Object> envProperties = new HashMap<>();
        envProperties.put("email.properties.STARTTLS.ENABLE", "true");
        envProperties.put("email.properties.AUTH", "true");
        envProperties.put("email.properties.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        EmailPropertiesLoader loader = new EmailPropertiesLoader();

        try (MockedStatic<EnvironmentUtils> mockedEnvironmentUtils = mockStatic(EnvironmentUtils.class)) {
            mockedEnvironmentUtils
                    .when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq(EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX)))
                    .thenReturn(envProperties);

            Properties properties = loader.load(environment, "basic");

            assertThat(properties).hasSize(3);
            assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.socketFactory.class"))
                    .isEqualTo("javax.net.ssl.SSLSocketFactory");
        }
    }

    @Test
    void shouldReturnEmptyPropertiesWhenNoneConfigured() {
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        EmailPropertiesLoader loader = new EmailPropertiesLoader();

        try (MockedStatic<EnvironmentUtils> mockedEnvironmentUtils = mockStatic(EnvironmentUtils.class)) {
            mockedEnvironmentUtils
                    .when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq(EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX)))
                    .thenReturn(Map.of());

            Properties properties = loader.load(environment, "basic");

            assertThat(properties).isEmpty();
        }
    }

    @Test
    void shouldLoadOAuthSettings() {
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        Map<String, Object> envProperties = new HashMap<>();
        envProperties.put("email.properties.STARTTLS.ENABLE", "true");
        envProperties.put("email.properties.AUTH", "true");
        envProperties.put("email.properties.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        EmailPropertiesLoader loader = new EmailPropertiesLoader();

        try (MockedStatic<EnvironmentUtils> mockedEnvironmentUtils = mockStatic(EnvironmentUtils.class)) {
            mockedEnvironmentUtils
                    .when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq(EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX)))
                    .thenReturn(envProperties);

            Properties properties = loader.load(environment, "oauth2");
            assertThat(properties).hasSize(6);

            assertThat(properties.getProperty("mail.smtp.auth.mechanisms")).isEqualTo("XOAUTH2");
            assertThat(properties.getProperty("mail.smtp.auth.plain.disable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.auth.login.disable")).isEqualTo("true");

            assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.socketFactory.class"))
                    .isEqualTo("javax.net.ssl.SSLSocketFactory");
        }
    }
}
