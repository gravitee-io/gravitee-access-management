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

import io.vertx.core.Future;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.impl.UserContextInternal;

public class SpyUserContext implements UserContextInternal {
    private User coreUser;

    @Override
    public void setUser(User user) { this.coreUser = user; }
    @Override
    public User get() { return coreUser; }
    @Override
    public io.vertx.ext.web.UserContext loginHint(String hint) { return this; }
    @Override
    public Future<Void> refresh() { return Future.succeededFuture(); }
    @Override
    public Future<Void> refresh(String provider) { return Future.succeededFuture(); }
    @Override
    public Future<Void> impersonate() { return Future.succeededFuture(); }
    @Override
    public Future<Void> impersonate(String provider) { return Future.succeededFuture(); }
    @Override
    public Future<Void> restore() { return Future.succeededFuture(); }
    @Override
    public Future<Void> restore(String provider) { return Future.succeededFuture(); }
    @Override
    public Future<Void> logout(String provider) { coreUser = null; return Future.succeededFuture(); }
    @Override
    public Future<Void> logout() { coreUser = null; return Future.succeededFuture(); }
    @Override
    public void clear() { coreUser = null; }
}
