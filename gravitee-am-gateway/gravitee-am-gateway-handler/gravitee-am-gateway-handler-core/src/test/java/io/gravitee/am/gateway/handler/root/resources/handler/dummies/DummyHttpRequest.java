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

package io.gravitee.am.gateway.handler.root.resources.handler.dummies;

import io.netty.handler.codec.DecoderResult;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
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
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.WriteStream;
import org.mockito.Mockito;

import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyHttpRequest implements HttpServerRequest {

    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    private final MultiMap params = MultiMap.caseInsensitiveMultiMap();

    private final MultiMap formAttributes = MultiMap.caseInsensitiveMultiMap();
    private final Map<String, Cookie> cookies = new HashMap<>();
    private HttpMethod method;

    private final String path;

    public DummyHttpRequest() {
        this(null);
    }
    public DummyHttpRequest(String path) {
        this.path = path;
    }

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
    public Pipe<Buffer> pipe() {
        return HttpServerRequest.super.pipe();
    }

    @Override
    public Future<Void> pipeTo(WriteStream<Buffer> dst) {
        return HttpServerRequest.super.pipeTo(dst);
    }

    @Override
    public void pipeTo(WriteStream<Buffer> dst, Handler<AsyncResult<Void>> handler) {
        HttpServerRequest.super.pipeTo(dst, handler);
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
    public @Nullable String scheme() {
        return null;
    }

    @Override
    public String uri() {
        return URI.create("http://client").toString();
    }

    @Override
    public @Nullable String path() {
        return path;
    }

    @Override
    public @Nullable String query() {
        return null;
    }

    @Override
    public @Nullable String host() {
        return null;
    }

    @Override
    public @Nullable HostAndPort authority() {
        return null;
    }

    @Override
    public @Nullable HostAndPort authority(boolean real) {
        return null;
    }

    @Override
    public long bytesRead() {
        return 0;
    }

    @Override
    public HttpServerResponse response() {
        return mock(HttpServerResponse.class);
    }

    @Override
    public MultiMap headers() {
        return headers;
    }

    @Override
    public @Nullable String getHeader(String headerName) {
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
    public @Nullable String getParam(String paramName) {
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
        return Mockito.mock(SocketAddress.class);
    }

    @Override
    public SSLSession sslSession() {
        return HttpServerRequest.super.sslSession();
    }

    @Override
    public X509Certificate[] peerCertificateChain() {
        return new X509Certificate[0];
    }

    @Override
    public String absoluteURI() {
        return null;
    }

    @Override
    public HttpServerRequest bodyHandler(@Nullable Handler<Buffer> bodyHandler) {
        return HttpServerRequest.super.bodyHandler(bodyHandler);
    }

    @Override
    public HttpServerRequest body(Handler<AsyncResult<Buffer>> handler) {
        return HttpServerRequest.super.body(handler);
    }

    @Override
    public Future<Buffer> body() {
        return null;
    }

    @Override
    public void end(Handler<AsyncResult<Void>> handler) {
        HttpServerRequest.super.end(handler);
    }

    @Override
    public Future<Void> end() {
        return null;
    }

    @Override
    public void toNetSocket(Handler<AsyncResult<NetSocket>> handler) {
        HttpServerRequest.super.toNetSocket(handler);
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
    public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> uploadHandler) {
        return null;
    }

    @Override
    public MultiMap formAttributes() {
        return formAttributes;
    }

    @Override
    public @Nullable String getFormAttribute(String attributeName) {
        return null;
    }

    @Override
    public int streamId() {
        return HttpServerRequest.super.streamId();
    }

    @Override
    public void toWebSocket(Handler<AsyncResult<ServerWebSocket>> handler) {
        HttpServerRequest.super.toWebSocket(handler);
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
    public @Nullable Cookie getCookie(String name) {
        return getCookie(name, null, null);
    }

    @Override
    public @Nullable Cookie getCookie(String name, String domain, String path) {
        return cookies.get(name);
    }

    @Override
    public int cookieCount() {
        return HttpServerRequest.super.cookieCount();
    }

    @Override
    public Map<String, Cookie> cookieMap() {
        return cookies;
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

    public void putCookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
    }
}
