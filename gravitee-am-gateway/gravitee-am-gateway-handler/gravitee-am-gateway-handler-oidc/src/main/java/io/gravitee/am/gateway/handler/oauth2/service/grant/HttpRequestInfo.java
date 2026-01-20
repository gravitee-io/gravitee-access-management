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
package io.gravitee.am.gateway.handler.oauth2.service.grant;

import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.Response;

/**
 * Immutable record containing HTTP request metadata.
 * Extracted from TokenRequest to provide only the necessary information
 * for token creation and audit purposes.
 *
 * @author GraviteeSource Team
 */
public record HttpRequestInfo(
        String id,
        String transactionId,
        String uri,
        String path,
        String pathInfo,
        String contextPath,
        String origin,
        String scheme,
        String remoteAddress,
        String localAddress,
        String host,
        HttpHeaders headers,
        MultiValueMap<String, String> parameters,
        HttpMethod method,
        HttpVersion version,
        long timestamp,
        String confirmationMethodX5S256,
        Response httpResponse
) {

    /**
     * Create HttpRequestInfo from a TokenRequest.
     *
     * @param request the token request
     * @return immutable HttpRequestInfo
     */
    public static HttpRequestInfo from(TokenRequest request) {
        return new HttpRequestInfo(
                request.id(),
                request.transactionId(),
                request.uri(),
                request.path(),
                request.pathInfo(),
                request.contextPath(),
                request.getOrigin(),
                request.scheme(),
                request.getRemoteAddress(),
                request.getLocalAddress(),
                request.host(),
                request.headers(),
                request.parameters(),
                request.method(),
                request.version(),
                request.timestamp(),
                request.getConfirmationMethodX5S256(),
                request.getHttpResponse()
        );
    }
}
