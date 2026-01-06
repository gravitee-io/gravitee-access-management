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
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

class EmailPropertiesLoader {

    static final String EMAIL_PROPERTIES_PREFIX = "email.properties";
    static final String AUTH_METHOD_BASIC = "basic";
    static final String AUTH_METHOD_OAUTH2 = "oauth2";

    private static final String MAILAPI_PROPERTIES_PREFIX = "mail.smtp.";

    Properties load(ConfigurableEnvironment environment, String authMethod) {
        Map<String, Object> envProperties = EnvironmentUtils.getPropertiesStartingWith(environment, EMAIL_PROPERTIES_PREFIX);

        Properties properties = new Properties();
        envProperties.forEach((key, value) -> {
            String propertyKey = key.substring(EMAIL_PROPERTIES_PREFIX.length() + 1);
            String normalizedKey = isAllCaps(propertyKey) ? propertyKey.toLowerCase(Locale.ROOT) : propertyKey;
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + normalizedKey, value.toString());
        });

        if (AUTH_METHOD_OAUTH2.equalsIgnoreCase(authMethod)) {
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.mechanisms", "XOAUTH2");
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.plain.disable", "true");
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.login.disable", "true");
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth", "true");
        }

        return properties;
    }

    private boolean isAllCaps(String value) {
        return value.equals(value.toUpperCase(Locale.ROOT));
    }
}
