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
/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.gravitee.am.gateway.handler.vertx.auth.http;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// TODO to remove when updating to vert.x 4
public final class SimpleHttpResponse {

    private final int statusCode;
    private final MultiMap headers;
    private final Buffer body;

    public SimpleHttpResponse(int statusCode, MultiMap headers, Buffer body) {
        this.headers = headers;
        this.body = body;
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public MultiMap headers() {
        return headers;
    }

    public String getHeader(String header) {
        if (headers != null) {
            return headers.get(header);
        }
        return null;
    }

    public Buffer body() {
        return body;
    }

    public @Nullable JsonObject jsonObject() {
        return new JsonObject(body);
    }

    public @Nullable JsonArray jsonArray() {
        return new JsonArray(body);
    }

    public boolean is(String contentType) {
        if (headers != null) {
            String header = headers.get("Content-Type");
            if (header != null) {
                int sep = header.indexOf(';');
                // exclude charset
                if (sep != -1) {
                    header = header.substring(0, sep).trim();
                }

                return contentType.equalsIgnoreCase(header);
            }
        }
        return false;
    }
}
