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

package io.gravitee.am.gateway.handler.dummies;

import io.gravitee.am.gateway.handler.common.vertx.web.handler.SpyUserContext;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
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
    private final DummyHttpRequest dummyHttpRequest = new DummyHttpRequest();
    private final HttpServerRequest httpServerRequest = new HttpServerRequest(dummyHttpRequest);
    private final DummySession dummySession = new DummySession();
    private final SpyUserContext sharedUserContext = new SpyUserContext();
    private int next = 0;

    public SpyRoutingContext() {
        super(null);
    }


    @Override
    public RoutingContext put(String key, Object obj) {
        data.put(key, obj);
        return this;
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
    public User user() {
        var coreUser = sharedUserContext.get();
        return coreUser != null ? User.newInstance(coreUser) : null;
    }

    @Override
    public UserContext userContext() {
        return new UserContext(sharedUserContext);
    }

    public void setUser(User user) {
        sharedUserContext.setUser(user != null ? user.getDelegate() : null);
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

    @Override
    public HttpServerRequest request() {
        return httpServerRequest;
    }

    public void putParam(String key, Object value) {
        dummyHttpRequest.putParam(key, value);
    }


    public void setMethod(HttpMethod method) {
        this.dummyHttpRequest.setMethod(method);
    }
}
