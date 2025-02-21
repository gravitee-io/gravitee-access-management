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
package io.gravitee.am.gateway.handler.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;

import static io.gravitee.am.gateway.core.LegacySettingsKeys.OIDC_SANITIZE_PARAM_ENCODING;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StaticEnvironmentProvider {

    private static Environment env = null;
    private static Boolean sanitizeParametersEncoding = null;

    public static void setEnvironment(Environment environment) {
        env = environment;
        // reset cached values
        sanitizeParametersEncoding = null;
    }

    public static boolean sanitizeParametersEncoding() {
        if (sanitizeParametersEncoding == null) {
            sanitizeParametersEncoding = OIDC_SANITIZE_PARAM_ENCODING.from(env);
        }
        return sanitizeParametersEncoding;
    }

    private static <T> T getEnvironmentProperty(String property, Class<T> type, T defaultValue) {
        return env != null ? env.getProperty(property, type, defaultValue) : defaultValue;
    }
}
