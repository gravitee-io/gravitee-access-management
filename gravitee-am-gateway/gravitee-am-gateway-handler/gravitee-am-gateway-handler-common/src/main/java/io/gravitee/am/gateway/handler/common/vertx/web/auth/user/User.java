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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.user;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.Authorizations;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class User implements io.vertx.ext.auth.User {

    private JsonObject principal;
    private io.gravitee.am.model.User user;

    public User(io.gravitee.am.model.User user) {
        this.user = user;
        this.principal = JsonObject.mapFrom(user);
    }

    public io.gravitee.am.model.User getUser() {
        return user;
    }

    @Override
    public JsonObject attributes() {
        return null;
    }

    @Override
    public boolean expired() {
        return io.vertx.ext.auth.User.super.expired();
    }

    @Override
    public boolean expired(int leeway) {
        return io.vertx.ext.auth.User.super.expired(leeway);
    }

    @Override
    public <T> @Nullable T get(String key) {
        return io.vertx.ext.auth.User.super.get(key);
    }

    @Override
    public boolean containsKey(String key) {
        return io.vertx.ext.auth.User.super.containsKey(key);
    }

    @Override
    public Authorizations authorizations() {
        return io.vertx.ext.auth.User.super.authorizations();
    }

    @Override
    public io.vertx.ext.auth.User isAuthorized(Authorization authorization, Handler<AsyncResult<Boolean>> handler) {
        return null;
    }

    @Override
    public JsonObject principal() {
        return principal;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {}

    @Override
    public io.vertx.ext.auth.User merge(io.vertx.ext.auth.User other) {
        return null;
    }
}
