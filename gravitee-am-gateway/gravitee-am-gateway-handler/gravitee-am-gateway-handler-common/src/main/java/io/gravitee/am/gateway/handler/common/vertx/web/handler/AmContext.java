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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.LanguageHeader;
import io.vertx.rxjava3.ext.web.RequestBody;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;

public class AmContext extends TypesafeMapAdapter {

    private static final String AM_CONTEXT_KEY = "__internal__am_ctx";
    private static final List<String> BLACKLIST_CONTEXT_ATTRIBUTES = Arrays.asList(AM_CONTEXT_KEY, "X-XSRF-TOKEN", "_csrf", "__body-handled");


    private final RoutingContext delegate;
    private AmSession session;

    /**
     * Exposes the underlying RoutingContext to simplify migration. Don't use for new code.
     *
     * @deprecated Prefer modifying code that relies on RoutingContext to use AmContext instead.
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    public RoutingContext delegate() {
        return delegate;
    }

    public AmSession session() {
        if (session == null) {
            session = new AmSession(delegate.session());
        }
        return session;
    }

    public static AmContext prepare(RoutingContext context) {
        var wrapper = new AmContext(context);
        if (!(context.get(AM_CONTEXT_KEY) instanceof AmContext)) {
            context.put(AM_CONTEXT_KEY, wrapper);
        }
        return wrapper;
    }

    public AmContext(RoutingContext delegate) {
        super(delegate::get);
        this.delegate = delegate;
    }

    public void setClient(Client client) {
        delegate.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
    }

    public Client getClient() {
        return typesafeGet(ConstantKeys.CLIENT_CONTEXT_KEY, Client.class);
    }

    public boolean isSilentAuth() {
        return getBoolean(ConstantKeys.SILENT_AUTH_CONTEXT_KEY);
    }

    public String getContextPath() {
        return typesafeGet(UriBuilderRequest.CONTEXT_PATH, String.class);
    }


    public io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User getUser() {
        return typesafeGet(USER_CONTEXT_KEY, io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
    }

    /**
     * Return the {@link RoutingContext#data()} entries without technical attributes defined in {@link #BLACKLIST_CONTEXT_ATTRIBUTES}
     * If {@link RoutingContext#data()} doesn't contain {@link ConstantKeys#USER_CONTEXT_KEY}, then the {@link RoutingContext#user()} is added if present
     *
     * @return a map containing evaluable attributes
     */
    public Map<String, Object> getEvaluableAttributes() {
        Map<String, Object> contextData = new HashMap<>(delegate.data());

        Object user = delegate.get(ConstantKeys.USER_CONTEXT_KEY); // might be User or UserProperties
        if (user != null) {
            contextData.put(ConstantKeys.USER_CONTEXT_KEY, user);
        } else if (delegate.user() != null) {
            contextData.put(ConstantKeys.USER_CONTEXT_KEY, ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) delegate.user().getDelegate()).getUser());
        }

        if (session() != null) {
            contextData.putAll(session.getEvaluableAttributes());
        }

        // remove technical attributes
        BLACKLIST_CONTEXT_ATTRIBUTES.forEach(contextData::remove);
        return contextData;
    }

    // ----------------------
    // RoutingContext methods
    // ----------------------
    public void next() {
        delegate.next();
    }

    public Completable end() {
        return delegate.end();
    }

    public void fail(int statusCode) {
        delegate.fail(statusCode);
    }

    public void fail(Throwable throwable) {
        delegate.fail(throwable);
    }

    public void fail(int statusCode, Throwable throwable) {
        delegate.fail(statusCode, throwable);
    }

    public boolean failed() {
        return delegate.failed();
    }

    public Throwable failure() {
        return delegate.failure();
    }

    public Map<String, Object> data() {
        return delegate.data();
    }

    public int statusCode() {
        return delegate.statusCode();
    }

    public HttpServerRequest request() {
        return delegate.request();
    }

    public HttpServerResponse response() {
        return delegate.response();
    }

    public RequestBody body() {
        return delegate.body();
    }

    @Deprecated(forRemoval = true)
    public JsonObject getBodyAsJson() {
        return body().asJsonObject();
    }

    public MultiMap queryParams() {
        return delegate.queryParams();
    }

    public List<String> queryParam(String name) {
        return delegate.queryParam(name);
    }

    @Deprecated(forRemoval = true)
    public Cookie getCookie(String name) {
        return request().getCookie(name);
    }

    @Deprecated(forRemoval = true)
    public AmContext addCookie(Cookie cookie) {
        response().addCookie(cookie);
        return this;
    }

    public User user() {
        return delegate.user();
    }

    public void setUser(User user) {
        delegate.setUser(user);
    }

    public void clearUser() {
        delegate.clearUser();
    }

    public List<LanguageHeader> acceptableLanguages() {
        return delegate.acceptableLanguages();
    }


    public void setGeoIp(Map<String, Object> map) {
        data().put(GEOIP_KEY, map);
    }
}
