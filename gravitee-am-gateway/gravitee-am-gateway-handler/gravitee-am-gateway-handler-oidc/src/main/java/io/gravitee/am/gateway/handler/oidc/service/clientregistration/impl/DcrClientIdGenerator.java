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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl;

import io.gravitee.am.common.utils.SecureRandomString;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Generates the client_id of the clients created through Dynamic Client Registration.
 * <p>
 * The length is an installation-wide setting ({@value #LENGTH_PROPERTY}). When it is not set, the
 * historical 43 characters URL-safe base64 identifier is generated.
 *
 * @author GraviteeSource Team
 */
public class DcrClientIdGenerator {

    public static final String LENGTH_PROPERTY = "handlers.oidc.dcr.clientIdLength";
    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 255;

    private final int length;

    public DcrClientIdGenerator(Environment environment) {
        this.length = parseLength(environment.getProperty(LENGTH_PROPERTY));
    }

    public String generate() {
        return length == 0 ? SecureRandomString.generate() : SecureRandomString.randomAlphaNumeric(length);
    }

    private static int parseLength(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= MIN_LENGTH && parsed <= MAX_LENGTH) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // reported below with the other invalid values
        }
        throw new IllegalStateException("Property " + LENGTH_PROPERTY + " must be an integer between " + MIN_LENGTH + " and " + MAX_LENGTH + ", got '" + value + "'");
    }
}
