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
package io.gravitee.am.gateway.handler.common.vertx.core.http;

import io.gravitee.gateway.api.Request;
import io.netty.handler.codec.DecoderResult;
import io.vertx.codegen.annotations.Nullable;
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
import io.vertx.core.net.impl.HostAndPortImpl;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeVertxHttpServerRequest implements HttpServerRequest {

    private Request delegate;
    private HttpVersion version;
    private HttpMethod method;
    private String scheme;
    private String uri;
    private String path;
    private String host;
    private HostAndPort authority;
    private MultiMap headers;
    private MultiMap params;
    private MultiMap attributes;
    private boolean expectMultipart;

    public GraviteeVertxHttpServerRequest(Request request) {
        delegate = request;
        version = HttpVersion.valueOf(request.version().toString());
        method = HttpMethod.valueOf(request.method().toString());
        scheme = request.scheme();
        uri = request.uri();
        path = request.path();
        host = request.host();
        if (request.headers() != null) {
            headers = MultiMap.caseInsensitiveMultiMap().addAll(request.headers().toSingleValueMap());
        }
        if (request.parameters() != null) {
            params = MultiMap.caseInsensitiveMultiMap().addAll(request.parameters().toSingleValueMap());
        }
        if (host != null) {
            authority = HostAndPortImpl.parseAuthority(host, -1);
        }
    }

    @Override
    public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    @Override
    public HttpServerRequest handler(Handler<Buffer> handler) {
        return this;
    }

    @Override
    public HttpServerRequest pause() {
        delegate.pause();
        return this;
    }

    @Override
    public HttpServerRequest resume() {
        delegate.resume();
        return this;
    }

    @Override
    public @Nullable HostAndPort authority() {
        if (delegate instanceof HttpServerRequest) {
            return ((HttpServerRequest) delegate).authority();
        }
        return HostAndPort.create(this.host, -1);
    }

    @Override
    public @Nullable HostAndPort authority(boolean real) {
        if (delegate instanceof HttpServerRequest) {
            return ((HttpServerRequest) delegate).authority(real);
        }
        return authority();
    }

    @Override
    public HttpServerRequest fetch(long l) {
        return this;
    }

    @Override
    public HttpServerRequest endHandler(Handler<Void> handler) {
        return this;
    }

    @Override
    public HttpVersion version() {
        return version;
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public @Nullable String scheme() {
        return scheme;
    }

    @Override
    public String uri() {
        return uri;
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
        return host;
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

    @Override
    public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
        return new X509Certificate[0];
    }

    @Override
    public String absoluteURI() {
        return null;
    }

    @Override
    public Future<Buffer> body() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end() {
        return Future.succeededFuture();
    }

    @Override
    public Future<NetSocket> toNetSocket() {
        return Future.succeededFuture();
    }

    @Override
    public HttpServerRequest setExpectMultipart(boolean b) {
        expectMultipart = b;
        return this;
    }

    @Override
    public boolean isExpectMultipart() {
        return expectMultipart;
    }

    @Override
    public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> handler) {
        return this;
    }

    @Override
    public MultiMap formAttributes() {
        if (attributes == null) {
            attributes = MultiMap.caseInsensitiveMultiMap();
        }
        return attributes;
    }

    @Override
    public @Nullable String getFormAttribute(String s) {
        return formAttributes().get(s);
    }

    @Override
    public Future<ServerWebSocket> toWebSocket() {
        return Future.succeededFuture();
    }

    @Override
    public boolean isEnded() {
        return delegate.ended();
    }

    @Override
    public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
        return this;
    }

    @Override
    public HttpConnection connection() {
        return null;
    }

    @Override
    public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
        return this;
    }

    @Override
    public DecoderResult decoderResult() {
        return null;
    }

    @Override
    public @Nullable Cookie getCookie(String s) {
        return null;
    }

    @Override
    public @Nullable Cookie getCookie(String s, String s1, String s2) {
        return null;
    }

    @Override
    public Set<Cookie> cookies(String s) {
        return null;
    }

    @Override
    public Set<Cookie> cookies() {
        return null;
    }

    @Override
    public MultiMap params(boolean b) {
        return this.params();
    }
}
