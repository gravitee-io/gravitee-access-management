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

import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.WriteStream;
import io.gravitee.reporter.api.http.Metrics;
import io.netty.buffer.ByteBuf;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxHttpServerResponse implements Response {

    private final HttpServerResponse httpServerResponse;

    private final HttpHeaders headers;

    private final Metrics metrics;

    private final HttpVersion version;

    private HttpHeaders trailers;

    public VertxHttpServerResponse(final HttpServerRequest httpServerRequest, final Metrics metrics) {
        this.httpServerResponse = httpServerRequest.response();
        this.version = httpServerRequest.version();
        this.headers = new VertxHttpHeaders(this.httpServerResponse.headers());
        this.trailers = new VertxHttpHeaders(this.httpServerResponse.trailers());
        this.metrics = metrics;
    }

    @Override
    public int status() {
        return httpServerResponse.getStatusCode();
    }

    @Override
    public String reason() {
        return httpServerResponse.getStatusMessage();
    }

    @Override
    public Response reason(String reason) {
        if (reason != null) {
            httpServerResponse.setStatusMessage(reason);
        }
        return this;
    }

    @Override
    public Response status(int statusCode) {
        httpServerResponse.setStatusCode(statusCode);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public boolean ended() {
        return httpServerResponse.ended();
    }

    @Override
    public HttpHeaders trailers() {
        return trailers;
    }

    @Override
    public Response write(Buffer chunk) {
        if (valid()) {
            if (!httpServerResponse.headWritten()) {
                writeHeaders();

                // Vertx requires to set the chunked flag if transfer_encoding header as the "chunked" value
                String transferEncodingHeader = headers().getFirst(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING);
                if (HttpHeadersValues.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncodingHeader)) {
                    httpServerResponse.setChunked(true);
                } else if (transferEncodingHeader == null) {
                    String connectionHeader = headers().getFirst(io.vertx.core.http.HttpHeaders.CONNECTION);
                    String contentLengthHeader = headers().getFirst(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH);
                    if (HttpHeadersValues.CONNECTION_CLOSE.equalsIgnoreCase(connectionHeader)
                            && contentLengthHeader == null) {
                        httpServerResponse.setChunked(true);
                    }
                }
            }

            metrics.setResponseContentLength(metrics.getResponseContentLength() + chunk.length());
            httpServerResponse.write(io.vertx.core.buffer.Buffer.buffer((ByteBuf) chunk.getNativeBuffer()));
        }
        return this;
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> drainHandler) {
        httpServerResponse.drainHandler((aVoid -> drainHandler.handle(null)));
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return valid() && httpServerResponse.writeQueueFull();
    }

    @Override
    public void end() {
        if (valid()) {
            if (!httpServerResponse.headWritten()) {
                writeHeaders();
            }

            httpServerResponse.end();
        }
    }

    private boolean valid() {
        return !httpServerResponse.closed() && !httpServerResponse.ended();
    }

    private void writeHeaders() {
        // As per https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        // connection-specific header fields must be remove from response headers
        headers.forEach(header -> {
            if (version == HttpVersion.HTTP_1_0 || version == HttpVersion.HTTP_1_1
                    || (!header.getKey().equalsIgnoreCase(io.vertx.core.http.HttpHeaders.CONNECTION.toString())
                    && !header.getKey().equalsIgnoreCase(io.vertx.core.http.HttpHeaders.KEEP_ALIVE.toString())
                    && !header.getKey().equalsIgnoreCase(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING.toString()))) {
                httpServerResponse.putHeader(header.getKey(), header.getValue());
            }
        });
    }
}
