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
package io.gravitee.am.identityprovider.http.utils;

import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class SanitizeUtils {

    /**
     * Sanitize user credentials if a JSON body is sent
     * @param credentials
     * @param requestBody
     * @param authenticationHttpHeaders
     * @return
     */
    public static String sanitize(String credentials, String requestBody, List<HttpHeader> authenticationHttpHeaders) {
        final List<String> contentTypeHeaders = authenticationHttpHeaders == null ? Collections.emptyList() :
                authenticationHttpHeaders
                        .stream()
                        .filter(httpHeader -> HttpHeaders.CONTENT_TYPE.equals(httpHeader.getName()))
                        .map(HttpHeader::getValue)
                        .collect(Collectors.toList());

        // if HTTP headers contains Content-Type == application/json
        // it means we except a json body content
        if (contentTypeHeaders.contains(MediaType.APPLICATION_JSON)) {
            return credentials.replaceAll("\"", "\\\\\"");
        }

        // if we can't rely on http headers, look into the body
        if (contentTypeHeaders.isEmpty() && requestBody != null && (requestBody.startsWith("{") || requestBody.startsWith("["))) {
            return credentials.replaceAll("\"", "\\\\\"");
        }

        return credentials;
    }
}
