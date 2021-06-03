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
package io.gravitee.am.management.handlers.management.api.authentication.http;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http2.HttpFrame;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.gateway.api.ws.WebSocket;
import io.gravitee.reporter.api.http.Metrics;

import javax.net.ssl.SSLSession;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JettyHttpServerRequest implements Request {

    private final String id;
    private final String transactionId;
    private final String contextPath;
    private final long timestamp;
    private final HttpServletRequest httpServerRequest;
    private MultiValueMap<String, String> queryParameters = null;
    private HttpHeaders headers = null;
    private MultiValueMap<String, String> pathParameters = null;

    public JettyHttpServerRequest(HttpServletRequest httpServerRequest) {
        this.httpServerRequest = httpServerRequest;
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.toString(UUID.random());
        this.transactionId = UUID.toString(UUID.random());
        this.contextPath = httpServerRequest.getContextPath() != null ? httpServerRequest.getContextPath().split("/")[0] : null;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String transactionId() {
        return transactionId;
    }

    @Override
    public String uri() {
        return httpServerRequest.getRequestURI();
    }

    @Override
    public String path() {
        return httpServerRequest.getPathInfo();
    }

    @Override
    public String pathInfo() {
        return path();
    }

    @Override
    public String contextPath() {
        return contextPath;
    }

    @Override
    public MultiValueMap<String, String> parameters() {
        if (queryParameters == null) {
            Map<String, String[]> parameters = httpServerRequest.getParameterMap();
            queryParameters = new LinkedMultiValueMap<>(parameters.size());
            parameters.forEach((k, v) -> queryParameters.put(k, Arrays.asList(v)));
        }
        return queryParameters;
    }

    @Override
    public MultiValueMap<String, String> pathParameters() {
        if (pathParameters == null) {
            pathParameters = new LinkedMultiValueMap<>();
        }

        return pathParameters;
    }

    @Override
    public HttpHeaders headers() {
        if (headers == null) {
            Enumeration<String> headerNames = httpServerRequest.getHeaderNames();
            if (headerNames != null) {
                headers = new HttpHeaders();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = httpServerRequest.getHeader(headerName);
                    headers.add(headerName, headerValue);
                }
            }
        }
        return headers;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.valueOf(httpServerRequest.getMethod());
    }

    @Override
    public String scheme() {
        return httpServerRequest.getScheme();
    }

    @Override
    public HttpVersion version() {
        return httpServerRequest.getProtocol().equals("HTTP/1.0") ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String remoteAddress() {
        return httpServerRequest.getRemoteAddr();
    }

    @Override
    public String localAddress() {
        return httpServerRequest.getLocalAddr();
    }

    @Override
    public SSLSession sslSession() {
        return null;
    }

    @Override
    public Metrics metrics() {
        return null;
    }

    @Override
    public boolean ended() {
        return false;
    }

    @Override
    public Request timeoutHandler(Handler<Long> timeoutHandler) {
        return null;
    }

    @Override
    public Handler<Long> timeoutHandler() {
        return null;
    }

    @Override
    public boolean isWebSocket() {
        return false;
    }

    @Override
    public WebSocket websocket() {
        return null;
    }

    @Override
    public Request customFrameHandler(Handler<HttpFrame> frameHandler) {
        return this;
    }

    @Override
    public ReadStream<Buffer> bodyHandler(Handler<Buffer> bodyHandler) {
        return null;
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
        return null;
    }
}
