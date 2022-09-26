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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientUtils {

    /**
     * @param value
     * @return the URL value version of value or the input value if the URLDecode fails
     */
    public static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, UTF_8);
        } catch (IllegalArgumentException e) {
            // Introduced to fix https://github.com/gravitee-io/issues/issues/8501.
            // https://github.com/gravitee-io/issues/issues/7803 introduced a URL decoding
            // action on the clientSecret/clientId to be compliant to the RFC. To avoid breaking the
            // behaviour for customer that are using some special characters like '%', we fall back to the
            // raw value if the URL decode process fails.
            return value;
        }
    }
}
