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


import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.streams.ReadStream;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyHttpResponse implements HttpServerResponse {

    private final io.vertx.core.MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    private boolean ended;
    private int statusCode;

    @Override
    public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
        return null;
    }

    @Override
    public Future<Void> write(Buffer data) {
        return null;
    }

    @Override
    public void write(Buffer data, Handler<AsyncResult<Void>> handler) {

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
    public HttpServerResponse closeHandler(@Nullable Handler<Void> handler) {
        return null;
    }

    @Override
    public HttpServerResponse endHandler(@Nullable Handler<Void> handler) {
        return null;
    }

    @Override
    public Future<Void> write(String chunk, String enc) {
        return null;
    }

    @Override
    public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public Future<Void> write(String chunk) {
        return null;
    }

    @Override
    public void write(String chunk, Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public HttpServerResponse writeContinue() {
        return null;
    }

    @Override
    public Future<Void> writeEarlyHints(MultiMap multiMap) {
        return null;
    }

    @Override
    public void writeEarlyHints(MultiMap multiMap, Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public Future<Void> end(String chunk) {
        this.ended = true;
        return Future.succeededFuture();
    }

    @Override
    public void end(String chunk, Handler<AsyncResult<Void>> handler) {
        this.ended = true;
    }

    @Override
    public Future<Void> end(String chunk, String enc) {
        this.ended = true;
        return Future.succeededFuture();
    }

    @Override
    public void end(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
        this.ended = true;
    }

    @Override
    public Future<Void> end(Buffer chunk) {
        this.ended = true;
        return Future.succeededFuture();
    }

    @Override
    public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) {
        this.ended = true;
    }

    @Override
    public Future<Void> end() {
        this.ended = true;
        return Future.succeededFuture();
    }

    @Override
    public void send(Handler<AsyncResult<Void>> handler) {
        HttpServerResponse.super.send(handler);
    }

    @Override
    public Future<Void> send() {
        return HttpServerResponse.super.send();
    }

    @Override
    public void send(String body, Handler<AsyncResult<Void>> handler) {
        HttpServerResponse.super.send(body, handler);
    }

    @Override
    public Future<Void> send(String body) {
        return HttpServerResponse.super.send(body);
    }

    @Override
    public void send(Buffer body, Handler<AsyncResult<Void>> handler) {
        HttpServerResponse.super.send(body, handler);
    }

    @Override
    public Future<Void> send(Buffer body) {
        return HttpServerResponse.super.send(body);
    }

    @Override
    public void send(ReadStream<Buffer> body, Handler<AsyncResult<Void>> handler) {
        HttpServerResponse.super.send(body, handler);
    }

    @Override
    public Future<Void> send(ReadStream<Buffer> body) {
        return HttpServerResponse.super.send(body);
    }

    @Override
    public Future<Void> sendFile(String filename) {
        return HttpServerResponse.super.sendFile(filename);
    }

    @Override
    public Future<Void> sendFile(String filename, long offset) {
        return HttpServerResponse.super.sendFile(filename, offset);
    }

    @Override
    public void end(Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public Future<Void> sendFile(String filename, long offset, long length) {
        return null;
    }

    @Override
    public HttpServerResponse sendFile(String filename, Handler<AsyncResult<Void>> resultHandler) {
        return HttpServerResponse.super.sendFile(filename, resultHandler);
    }

    @Override
    public HttpServerResponse sendFile(String filename, long offset, Handler<AsyncResult<Void>> resultHandler) {
        return HttpServerResponse.super.sendFile(filename, offset, resultHandler);
    }

    @Override
    public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }

    @Override
    public void close() {

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
    public HttpServerResponse headersEndHandler(@Nullable Handler<Void> handler) {
        return null;
    }

    @Override
    public HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler) {
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
    public HttpServerResponse push(HttpMethod method, String host, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
        return HttpServerResponse.super.push(method, host, path, handler);
    }

    @Override
    public Future<HttpServerResponse> push(HttpMethod method, String host, String path) {
        return HttpServerResponse.super.push(method, host, path);
    }

    @Override
    public HttpServerResponse push(HttpMethod method, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
        return HttpServerResponse.super.push(method, path, headers, handler);
    }

    @Override
    public Future<HttpServerResponse> push(HttpMethod method, String path, MultiMap headers) {
        return HttpServerResponse.super.push(method, path, headers);
    }

    @Override
    public HttpServerResponse push(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
        return HttpServerResponse.super.push(method, path, handler);
    }

    @Override
    public Future<HttpServerResponse> push(HttpMethod method, String path) {
        return HttpServerResponse.super.push(method, path);
    }

    @Override
    public HttpServerResponse push(HttpMethod method, String host, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
        return HttpServerResponse.super.push(method, host, path, headers, handler);
    }

    @Override
    public Future<HttpServerResponse> push(HttpMethod method, String host, String path, MultiMap headers) {
        return null;
    }

    @Override
    public boolean reset() {
        return HttpServerResponse.super.reset();
    }

    @Override
    public boolean reset(long code) {
        return false;
    }

    @Override
    public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
        return null;
    }

    @Override
    public HttpServerResponse writeCustomFrame(HttpFrame frame) {
        return HttpServerResponse.super.writeCustomFrame(frame);
    }

    @Override
    public HttpServerResponse setStreamPriority(StreamPriority streamPriority) {
        return HttpServerResponse.super.setStreamPriority(streamPriority);
    }

    @Override
    public HttpServerResponse addCookie(Cookie cookie) {
        return null;
    }

    @Override
    public @Nullable Cookie removeCookie(String name) {
        return HttpServerResponse.super.removeCookie(name);
    }

    @Override
    public @Nullable Cookie removeCookie(String name, boolean invalidate) {
        return null;
    }

    @Override
    public Set<Cookie> removeCookies(String name) {
        return HttpServerResponse.super.removeCookies(name);
    }

    @Override
    public Set<Cookie> removeCookies(String name, boolean invalidate) {
        return null;
    }

    @Override
    public @Nullable Cookie removeCookie(String name, String domain, String path) {
        return HttpServerResponse.super.removeCookie(name, domain, path);
    }

    @Override
    public @Nullable Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
        return null;
    }
}
