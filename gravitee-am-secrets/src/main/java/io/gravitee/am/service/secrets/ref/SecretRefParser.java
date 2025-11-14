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
package io.gravitee.am.service.secrets.ref;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author GraviteeSource Team
 */
public class SecretRefParser {
    private static final Pattern PATTERN = Pattern.compile(
            "^/(?<provider>[^/]+)/(?<path>[^:?]+)" +
                    "(?::(?<key>[^?]+))?" +
                    "(?:\\?(?<query>.*))?$"
    );

    public static SecretRef parse(String input) {
        Matcher m = PATTERN.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid SecretRef: " + input);
        }

        String provider = m.group("provider");
        String path = m.group("path");
        String key = m.group("key");
        String query = m.group("query");

        Multimap<String, String> params = parseQueryParams(query);
        return new SecretRef(provider, path, key, params);
    }

    private static Multimap<String, String> parseQueryParams(String query) {
        Multimap<String, String> params = ArrayListMultimap.create();
        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                params.put(part.substring(0, eq), part.substring(eq + 1));
            } else {
                params.put(part, "");
            }
        }

        return params;
    }
}
