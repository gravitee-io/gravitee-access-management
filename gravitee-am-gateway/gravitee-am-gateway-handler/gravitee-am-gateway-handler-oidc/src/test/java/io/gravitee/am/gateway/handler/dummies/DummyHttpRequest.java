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

package io.gravitee.am.gateway.handler.dummies;

import io.netty.handler.codec.DecoderResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLSession;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyHttpRequest implements HttpServerRequest {

    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    private final MultiMap params = MultiMap.caseInsensitiveMultiMap();
    private HttpMethod method;

    @Override
    public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
        return null;
    }

    @Override
    public HttpServerRequest handler(Handler<Buffer> handler) {
        return null;
    }

    @Override
    public HttpServerRequest pause() {
        return null;
    }

    @Override
    public HttpServerRequest resume() {
        return null;
    }

    @Override
    public HttpServerRequest fetch(long amount) {
        return null;
    }

    @Override
    public HttpServerRequest endHandler(Handler<Void> endHandler) {
        return null;
    }

    @Override
    public HttpVersion version() {
        return null;
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    @Override
    public boolean isSSL() {
        return HttpServerRequest.super.isSSL();
    }

    @Override
    public String scheme() {
        return null;
    }

    @Override
    public String uri() {
        return null;
    }

    @Override
    public String path() {
        return null;
    }

    @Override
    public String query() {
        return null;
    }

    @Override
    public HostAndPort authority() {
        return null;
    }

    @Override
    public HostAndPort authority(boolean real) {
        return null;
    }

    @Override
    public long bytesRead() {
        return 0;
    }

    @Override
    public HttpServerResponse response() {
        return null;
    }

    @Override
    public MultiMap headers() {
        return headers;
    }

    @Override
    public String getHeader(String headerName) {
        return HttpServerRequest.super.getHeader(headerName);
    }

    @Override
    public String getHeader(CharSequence headerName) {
        return HttpServerRequest.super.getHeader(headerName);
    }

    @Override
    public HttpServerRequest setParamsCharset(String s) {
        return null;
    }

    @Override
    public String getParamsCharset() {
        return null;
    }

    @Override
    public MultiMap params() {
        return params;
    }

    public void putParam(String key, Object value) {
        params.add(key, String.valueOf(value));
    }

    @Override
    public String getParam(String paramName) {
        return params.get(paramName);
    }

    @Override
    public String getParam(String paramName, String defaultValue) {
        return HttpServerRequest.super.getParam(paramName, defaultValue);
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    public SocketAddress localAddress() {
        return HttpServerRequest.super.localAddress();
    }

    @Override
    public SSLSession sslSession() {
        return HttpServerRequest.super.sslSession();
    }

    @Override
    public String absoluteURI() {
        return null;
    }

    @Override
    public HttpServerRequest bodyHandler(Handler<Buffer> bodyHandler) {
        return HttpServerRequest.super.bodyHandler(bodyHandler);
    }

    @Override
    public Future<Buffer> body() {
        return null;
    }

    @Override
    public Future<Void> end() {
        return null;
    }

    @Override
    public Future<NetSocket> toNetSocket() {
        return null;
    }

    @Override
    public HttpServerRequest setExpectMultipart(boolean expect) {
        return null;
    }

    @Override
    public boolean isExpectMultipart() {
        return false;
    }

    @Override
    public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler) {
        return null;
    }

    @Override
    public MultiMap formAttributes() {
        return null;
    }

    @Override
    public String getFormAttribute(String attributeName) {
        return null;
    }

    @Override
    public int streamId() {
        return HttpServerRequest.super.streamId();
    }

    @Override
    public Future<ServerWebSocket> toWebSocket() {
        return null;
    }

    @Override
    public boolean isEnded() {
        return false;
    }

    @Override
    public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
        return null;
    }

    @Override
    public HttpConnection connection() {
        return null;
    }

    @Override
    public StreamPriority streamPriority() {
        return HttpServerRequest.super.streamPriority();
    }

    @Override
    public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
        return null;
    }

    @Override
    public DecoderResult decoderResult() {
        return null;
    }

    @Override
    public Cookie getCookie(String name) {
        return getCookie(name, null, null);
    }

    @Override
    public Cookie getCookie(String name, String domain, String path) {
        return null;
    }

    @Override
    public int cookieCount() {
        return HttpServerRequest.super.cookieCount();
    }

    @Override
    public Set<Cookie> cookies(String name) {
        return null;
    }

    @Override
    public Set<Cookie> cookies() {
        return null;
    }

    @Override
    public HttpServerRequest routed(String route) {
        return HttpServerRequest.super.routed(route);
    }

    @Override
    public MultiMap params(boolean semicolonIsNormalChar) {
        return this.params();
    }
}
