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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.gateway.api.ws.WebSocket;
import io.gravitee.reporter.api.http.Metrics;

import javax.net.ssl.SSLSession;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class BaseRequest implements Request {

    private String id;
    private String transactionId;
    private String uri;
    private String path;
    private String pathInfo;
    private String contextPath;
    private MultiValueMap<String, String> parameters;
    private HttpHeaders headers;
    private HttpMethod method;
    private String scheme;
    private String rawMethod;
    private long timestamp;
    private String remoteAddress;
    private String localAddress;
    private HttpVersion version;
    private SSLSession sslSession;
    private Response httpResponse;

    @Override
    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String transactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String uri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public String path() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String pathInfo() {
        return path();
    }

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    @Override
    public String contextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public MultiValueMap<String, String> parameters() {
        return parameters;
    }

    public void setParameters(MultiValueMap<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public HttpVersion version() {
        return version;
    }

    public void setVersion(HttpVersion version) {
        this.version = version;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String remoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String localAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    @Override
    public SSLSession sslSession() {
        return sslSession;
    }

    public void setSslSession(SSLSession sslSession) {
        this.sslSession = sslSession;
    }

    /**
     * Request origin : scheme/host/port triple.
     */
    private String origin;

    /**
     * All query parameters that do not belong to the OAuth 2.0/OIDC specifications
     */
    private MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public MultiValueMap<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(MultiValueMap<String, String> additionalParameters) {
        this.additionalParameters = additionalParameters;
    }

    /**
     * ---------------------------------------------
     * ---------------------------------------------
     * The following getters are mainly used for evaluable context (EL)
     * ---------------------------------------------
     * ---------------------------------------------
     */
    public String getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getUri() {
        return uri;
    }

    public String getPath() {
        return path;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public String getContextPath() {
        return contextPath;
    }

    public MultiValueMap<String, String> getParameters() {
        return parameters;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getScheme() {
        return scheme;
    }

    public String getRawMethod() {
        return rawMethod;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public HttpVersion getVersion() {
        return version;
    }

    public SSLSession getSslSession() {
        return sslSession;
    }

    public Response getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(Response httpResponse) {
        this.httpResponse = httpResponse;
    }

    /**
     * ---------------------------------------------
     * ---------------------------------------------
     * The following information is not required for the OAuth 2.0/OIDC use cases
     * ---------------------------------------------
     * ---------------------------------------------
     */

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
    public ReadStream<Buffer> bodyHandler(Handler<Buffer> bodyHandler) {
        return null;
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
        return null;
    }
}
