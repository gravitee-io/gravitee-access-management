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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AuthHandlerImpl extends io.vertx.ext.web.handler.impl.AuthHandlerImpl implements AuthHandler {

    protected static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);

    public AuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }

    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        parseAuthorization(
            context,
            parseAuthorization -> {
                if (parseAuthorization.failed()) {
                    handler.handle(Future.failedFuture(parseAuthorization.cause()));
                    return;
                }

                handler.handle(Future.succeededFuture(parseAuthorization.result()));
            }
        );
    }

    protected abstract void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler);
}
