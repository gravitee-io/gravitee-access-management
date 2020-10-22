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

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;


/**
 * A simple web client that does only depend on core to avoid cyclic dependencies.
 * The client is very simple, it allows fetching/storing a resource but doesn not do
 * any fancy transformations.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public final class SimpleHttpClient {

    private final WebClient client;

    public SimpleHttpClient(Vertx vertx, String userAgent, HttpClientOptions options) {
        WebClientOptions webClientOptions = new WebClientOptions();
        // specific UA
        if (userAgent != null) {
            webClientOptions.setUserAgent(userAgent);
        }
        this.client = WebClient.create(vertx, webClientOptions);
    }

    public Future<SimpleHttpResponse> fetch(HttpMethod method, String url, JsonObject headers, Buffer payload) {
        final Promise<SimpleHttpResponse> promise = Promise.promise();

        if (url == null || url.length() == 0) {
            promise.fail("Invalid url");
            return promise.future();
        }

        if (method != HttpMethod.POST && method != HttpMethod.PATCH && method != HttpMethod.PUT) {
            payload = null;
        }

        // create a request
        makeRequest(method, url, headers, payload, promise);
        return promise.future();
    }

    public SimpleHttpClient fetch(HttpMethod method, String url, JsonObject headers, Buffer payload, Handler<AsyncResult<SimpleHttpResponse>> callback) {
        fetch(method, url, headers, payload).onComplete(callback);
        return this;
    }

    public static Buffer jsonToQuery(JsonObject json) {
        Buffer buffer = Buffer.buffer();

        try {
            for (Map.Entry<String, ?> kv : json) {
                if (buffer.length() != 0) {
                    buffer.appendByte((byte) '&');
                }
                buffer.appendString(URLEncoder.encode(kv.getKey(), "UTF-8"));
                buffer.appendByte((byte) '=');
                Object v = kv.getValue();
                if (v != null) {
                    buffer.appendString(URLEncoder.encode(v.toString(), "UTF-8"));
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return buffer;
    }

    public static JsonObject queryToJson(Buffer query) throws UnsupportedEncodingException {
        if (query == null) {
            return null;
        }
        final JsonObject json = new JsonObject();
        final String[] pairs = query.toString().split("&");
        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
            final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            if (!json.containsKey(key)) {
                json.put(key, value);
            } else {
                Object oldValue = json.getValue(key);
                JsonArray array;
                if (oldValue instanceof JsonArray) {
                    array = (JsonArray) oldValue;
                } else {
                    array = new JsonArray();
                    array.add(oldValue);
                    json.put(key, array);
                }
                if (value == null) {
                    array.addNull();
                } else {
                    array.add(value);
                }
            }
        }

        return json;
    }


    private void makeRequest(HttpMethod method, String url, JsonObject headers, Buffer payload, final Handler<AsyncResult<SimpleHttpResponse>> callback) {
        HttpRequest<Buffer> request = client.requestAbs(method, url);
        // apply the provider required headers
        if (headers != null) {
            for (Map.Entry<String, Object> kv : headers) {
                request.headers().set(kv.getKey(), (String) kv.getValue());
            }
        }

        // set handler
        final Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler = send -> {
            if (send.failed()) {
                callback.handle(Future.failedFuture(send.cause()));
                return;
            }
            final HttpResponse<Buffer> res = send.result();
            Buffer value = res.body();

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                if (value == null || value.length() == 0) {
                    callback.handle(Future.failedFuture(res.statusMessage()));
                } else {
                    callback.handle(Future.failedFuture(res.statusMessage() + ": " + value.toString()));
                }
            } else {
                callback.handle(Future.succeededFuture(new SimpleHttpResponse(res.statusCode(), res.headers(), value)));
            }
        };

        // send
        if (payload != null) {
            request.sendBuffer(payload, resultHandler);
        } else {
            request.send(resultHandler);
        }
    }
}
