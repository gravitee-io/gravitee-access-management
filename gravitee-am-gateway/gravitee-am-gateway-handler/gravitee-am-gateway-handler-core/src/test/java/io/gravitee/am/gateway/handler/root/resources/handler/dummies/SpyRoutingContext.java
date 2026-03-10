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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.UserContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SpyRoutingContext extends RoutingContext {

    Map<String, Object> data = new HashMap<>();
    private final DummyHttpRequest dummyHttpRequest;
    private final HttpServerRequest httpServerRequest;
    private final DummySession dummySession = new DummySession();
    private final DummyHttpResponse response = new DummyHttpResponse();

    private int next = 0;
    private Buffer body;
    private User user;
    private int statusCode;
    private boolean failed;

    public SpyRoutingContext() {
        this(null);
    }

    public SpyRoutingContext(String path) {
        super(null);
        dummyHttpRequest = new DummyHttpRequest(path);
        httpServerRequest = new HttpServerRequest(dummyHttpRequest);
    }

    @Override
    public RoutingContext put(String key, Object obj) {
        data.put(key, obj);
        return this;
    }

    @Override
    public <T> T remove(String key) {
        return (T) data.remove(key);
    }

    public void setBody(Buffer body) {
        this.body = body;
    }

    public JsonObject getBodyAsJson() {
        return this.body == null ? null : this.body.toJsonObject();
    }

    @Override
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    @Override
    public Map<String, Object> data() {
        return data;
    }

    @Override
    public Session session() {
        return dummySession;
    }

    @Override
    public void next() {
        next++;
    }

    public boolean verifyNext(int expected){
        return next == expected;
    }

    public boolean ended(){
        return response.ended();
    }

    @Override
    public User user() {
        return user;
    }

    @Override
    public UserContext userContext() {
        return new UserContext(new io.vertx.ext.web.UserContext() {
            @Override
            public io.vertx.ext.auth.User get() {
                return user != null ? user.getDelegate() : null;
            }

            @Override
            public io.vertx.ext.web.UserContext loginHint(String hint) {
                return this;
            }

            @Override
            public Future<Void> refresh() {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> refresh(String provider) {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> impersonate() {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> impersonate(String provider) {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> restore() {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> restore(String provider) {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> logout(String provider) {
                user = null;
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> logout() {
                user = null;
                return Future.succeededFuture();
            }

            @Override
            public void clear() {
                user = null;
            }
        });
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public HttpServerRequest request() {
        return httpServerRequest;
    }

    @Override
    public void fail(int statusCode) {
        this.statusCode = statusCode;
        this.failed = true;
    }

    @Override
    public boolean failed() {
        return failed;
    }

    @Override
    public void fail(Throwable throwable) {
        this.failed = true;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpServerResponse response() {
        return new DummyReactiveHttpRequest(response);
    }

    public void putParam(String key, Object value) {
        dummyHttpRequest.putParam(key, value);
    }


    public void setMethod(HttpMethod method) {
        this.dummyHttpRequest.setMethod(method);
    }
}
