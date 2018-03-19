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
package io.gravitee.am.identityprovider.github.utils;

import io.gravitee.am.model.http.NameValuePair;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class URLEncodedUtils {

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final char QP_SEP_A = '&';
    private static final String NAME_VALUE_SEPARATOR = "=";

    public static String format(final Iterable<? extends NameValuePair> parameters) {
        final StringBuilder result = new StringBuilder();
        for (final NameValuePair parameter : parameters) {
            final String parameterName = parameter.getName();
            final String parameterValue = parameter.getValue();
            if (result.length() > 0) {
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

    public static Map<String, String> format(String query) {
        Map<String, String> queryPairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return queryPairs;
    }

}
