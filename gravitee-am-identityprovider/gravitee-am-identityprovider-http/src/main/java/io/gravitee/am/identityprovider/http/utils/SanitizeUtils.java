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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SanitizeUtils {

    /**
     * Sanitize user credentials if a JSON body is sent
     * @param credentials the user credentials to be sanitized.
     * @param requestBody the body of the HTTP request, which may contain JSON content.
     * @param authenticationHttpHeaders a list of HTTP headers from the authentication request.
     * @return the sanitized credentials if JSON content is detected, otherwise the original credentials.
     */
    public static String sanitize(String credentials, String requestBody, List<HttpHeader> authenticationHttpHeaders) {
        final List<String> contentTypeHeaders = authenticationHttpHeaders == null ? Collections.emptyList() :
                authenticationHttpHeaders
                        .stream()
                        .filter(httpHeader -> HttpHeaders.CONTENT_TYPE.equals(httpHeader.getName()))
                        .map(HttpHeader::getValue)
                        .toList();

        // if HTTP headers contains Content-Type == application/json
        // it means we except a json body content
        if (contentTypeHeaders.contains(MediaType.APPLICATION_JSON)) {
            return credentials.replace("\"", "\\\"");
        }

        // if we can't rely on http headers, look into the body
        if (contentTypeHeaders.isEmpty() && requestBody != null && (requestBody.startsWith("{") || requestBody.startsWith("["))) {
            return credentials.replace("\"", "\\\"");
        }
        return credentials;
    }
}
