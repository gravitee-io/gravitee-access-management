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
package io.gravitee.am.service.utils.vertx;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

public class BoundedBufferWriteStream implements io.vertx.core.streams.WriteStream<Buffer> {

    private final long maxResponseSize;
    private final Buffer body = Buffer.buffer();
    private Handler<Throwable> exceptionHandler;

    public BoundedBufferWriteStream(long maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
        if(maxResponseSize <= 0) {
            throw new IllegalArgumentException("maxResponseSize must be greater than 0");
        }
    }

    public Buffer body() {
        return body;
    }

    public static final class MaxResponseSizeExceededException extends IllegalStateException {
        public MaxResponseSizeExceededException(String message) {
            super(message);
        }
    }

    @Override
    public io.vertx.core.streams.WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
        if (data == null || data.length() == 0) {
            return Future.succeededFuture();
        }

        if (maxResponseSize > 0 && body.length() + (long) data.length() > maxResponseSize) {
            final var exception = new MaxResponseSizeExceededException("Response exceeds configured max size.");
            if (exceptionHandler != null) {
                exceptionHandler.handle(exception);
            }
            return Future.failedFuture(exception);
        }

        body.appendBuffer(data);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end(Buffer data) {
        return write(data);
    }

    @Override
    public io.vertx.core.streams.WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public io.vertx.core.streams.WriteStream<Buffer> drainHandler(Handler<Void> handler) {
        return this;
    }
}
