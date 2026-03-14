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


import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.net.HostAndPort;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyHttpResponse implements HttpServerResponse {

    private final io.vertx.core.MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    private boolean ended;
    private int statusCode;
    private Handler<Void> endHandler;

    private void markEnded() {
        this.ended = true;
        if (endHandler != null) {
            endHandler.handle(null);
        }
    }

    @Override
    public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
        return null;
    }

    @Override
    public Future<Void> write(Buffer data) {
        return Future.succeededFuture();
    }

    @Override
    public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
        return null;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public HttpServerResponse drainHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public HttpServerResponse setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    @Override
    public String getStatusMessage() {
        return null;
    }

    @Override
    public HttpServerResponse setStatusMessage(String statusMessage) {
        return null;
    }

    @Override
    public HttpServerResponse setChunked(boolean chunked) {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public MultiMap headers() {
        return headers;
    }

    @Override
    public HttpServerResponse putHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpServerResponse putHeader(String name, Iterable<String> values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public MultiMap trailers() {
        return null;
    }

    @Override
    public HttpServerResponse putTrailer(String name, String value) {
        return null;
    }

    @Override
    public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
        return null;
    }

    @Override
    public HttpServerResponse putTrailer(String name, Iterable<String> values) {
        return null;
    }

    @Override
    public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
        return null;
    }

    @Override
    public HttpServerResponse closeHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public HttpServerResponse endHandler(Handler<Void> handler) {
        this.endHandler = handler;
        return this;
    }

    @Override
    public Future<Void> writeHead() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> write(String chunk, String enc) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> write(String chunk) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> writeContinue() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> writeEarlyHints(MultiMap multiMap) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end(String chunk) {
        markEnded();
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end(String chunk, String enc) {
        markEnded();
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end(Buffer chunk) {
        markEnded();
        return Future.succeededFuture();
    }

    @Override
    public Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path, MultiMap headers) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end() {
        markEnded();
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> sendFile(String filename, long offset, long length) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> sendFile(FileChannel file, long offset, long length) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> sendFile(RandomAccessFile file, long offset, long length) {
        return Future.succeededFuture();
    }

    @Override
    public boolean ended() {
        return ended;
    }

    @Override
    public boolean closed() {
        return false;
    }

    @Override
    public boolean headWritten() {
        return false;
    }

    @Override
    public HttpServerResponse headersEndHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public HttpServerResponse bodyEndHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public long bytesWritten() {
        return 0;
    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public Future<Void> reset(long code) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
        return Future.succeededFuture();
    }

    @Override
    public HttpServerResponse addCookie(Cookie cookie) {
        return null;
    }

    @Override
    public Cookie removeCookie(String name, boolean invalidate) {
        return null;
    }

    @Override
    public Set<Cookie> removeCookies(String name, boolean invalidate) {
        return null;
    }

    @Override
    public Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
        return null;
    }
}
