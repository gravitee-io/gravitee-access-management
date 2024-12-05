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
package io.gravitee.am.identityprovider.common.oauth2.utils;

import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.am.model.http.NameValuePair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 * @deprecated use {@link URLParametersUtils} instead. This class is only left for compatibility reasons
 */
@Deprecated(since = "4.6.0")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class URLEncodedUtils {

    public static final String CONTENT_TYPE = URLParametersUtils.CONTENT_TYPE;

    public static String format(final Iterable<? extends NameValuePair> parameters) {
        return URLParametersUtils.format(parameters);
    }

    public static Map<String, String> format(String query) {
        return URLParametersUtils.parse(query);
    }

}
