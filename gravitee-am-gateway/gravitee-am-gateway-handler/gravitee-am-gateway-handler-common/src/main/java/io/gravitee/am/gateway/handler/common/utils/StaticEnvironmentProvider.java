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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StaticEnvironmentProvider {
    public static final String GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING = "legacy.openid.sanitizeParametersEncoding";
    public static final String GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS = "legacy.http.includeDefaultHostPorts";

    private static Environment env = null;
    private static Boolean sanitizeParametersEncoding = null;
    private static Boolean includeDefaultPorts = null;

    public static void setEnvironment(Environment environment) {
        env = environment;
        // reset cached values
        sanitizeParametersEncoding = null;
        includeDefaultPorts = null;
    }

    public static Environment getEnvironment() {
        return env;
    }

    public static boolean sanitizeParametersEncoding() {
        if (sanitizeParametersEncoding == null) {
            sanitizeParametersEncoding = getEnvironmentProperty(GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true);
        }
        return sanitizeParametersEncoding;
    }

    public static boolean includeDefaultHttpHostHeaderPorts() {
        if (includeDefaultPorts == null) {
            includeDefaultPorts = getEnvironmentProperty(GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false);
        }
        return includeDefaultPorts;
    }

    private static <T> T getEnvironmentProperty(String property, Class<T> type, T defaultValue) {
        return env != null ? env.getProperty(property, type, defaultValue) : defaultValue;
    }
}
