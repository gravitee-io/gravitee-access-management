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
package io.vertx.rxjava3.core.buffer;

/**
 * Stub class to satisfy compile-time references from gravitee-gateway-api's Buffer/BufferFactory
 * interfaces, which reference io.vertx.rxjava3.core.buffer.Buffer. This class was removed in
 * Vert.x 5 (rxjava3 Buffer wrapper was eliminated in favor of using io.vertx.core.buffer.Buffer
 * directly). This stub allows overload resolution to succeed at compile time.
 */
public class Buffer {

    private final io.vertx.core.buffer.Buffer delegate;

    public Buffer(io.vertx.core.buffer.Buffer delegate) {
        this.delegate = delegate;
    }

    public io.vertx.core.buffer.Buffer getDelegate() {
        return delegate;
    }
}
