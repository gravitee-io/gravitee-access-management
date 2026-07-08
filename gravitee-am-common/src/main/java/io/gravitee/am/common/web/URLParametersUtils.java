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
package io.gravitee.am.common.web;

import io.gravitee.am.common.utils.Pair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class URLParametersUtils {

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String QP_SEP_A = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";

    /**
     * Splits a query string on a '&' only when it introduces a new "name=" pair (positive lookahead).
     * Any other '&' — e.g. one embedded, unencoded, inside a value — is left untouched, so values
     * may safely contain one or several '&'.
     */
    private static final Pattern PAIR_SEPARATOR = Pattern.compile("&(?=[^&=]+=)");

    public static String format(final Iterable<? extends Pair<String,String>> parameters) {
        final StringBuilder result = new StringBuilder();
        for (final var parameter : parameters) {
            final String parameterName = parameter.getKey();
            final String parameterValue = parameter.getValue();
            if (!result.isEmpty()) {
                result.append(QP_SEP_A);
            }
            result.append(parameterName);
            if (parameterValue != null) {
                result.append(NAME_VALUE_SEPARATOR);
                result.append(parameterValue);
            }
        }
        return result.toString();
    }

    public static Map<String, String> parse(String query) {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        for (String pair : PAIR_SEPARATOR.split(query)) {
            int idx = pair.indexOf(NAME_VALUE_SEPARATOR);
            if (idx < 0) {
                // Not a name=value pair (no leading key to attach it to): ignore.
                continue;
            }
            queryPairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return queryPairs;
    }

}
