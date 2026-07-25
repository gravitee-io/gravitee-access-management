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
package io.gravitee.am.reporter.elasticsearch;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Endpoint URLs for logging, with any embedded credentials removed.
 * <p>
 * The reporter has dedicated username and password settings, so {@code scheme://user:pass@host} is
 * not the documented way to authenticate — but nothing rejects it, and the endpoints are logged
 * every time the cluster is unreachable, which is repeatedly and precisely when someone is reading
 * the logs.
 *
 * @author GraviteeSource Team
 */
public final class LoggableEndpoints {

    /** Matches the userinfo component: a scheme, then anything up to the first {@code @} of the authority. */
    private static final Pattern USER_INFO = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*://)([^/?#@]+)@");

    private LoggableEndpoints() {
    }

    public static List<String> redact(List<String> endpoints) {
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.stream().map(LoggableEndpoints::redact).toList();
    }

    private static String redact(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        return USER_INFO.matcher(endpoint).replaceFirst("$1***@");
    }
}
