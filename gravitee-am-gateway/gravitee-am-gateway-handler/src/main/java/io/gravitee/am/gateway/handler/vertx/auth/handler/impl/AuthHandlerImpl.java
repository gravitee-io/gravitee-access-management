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
package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AuthHandlerImpl extends io.vertx.ext.web.handler.impl.AuthHandlerImpl {

    protected static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);


    public AuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }

    /**
     * Override default parseCredentials method to set proper OAuth 2.0 invalid_client error response
     *
     * invalid_client
     * Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method).
     * The authorization server MAY return an HTTP 401 (Unauthorized) status code to indicate which HTTP authentication schemes are supported.  If the
     * client attempted to authenticate via the "Authorization" request header field, the authorization server MUST respond with an HTTP 401 (Unauthorized) status code and
     * include the "WWW-Authenticate" response header field matching the authentication scheme used by the client.
     */
    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        parseAuthorization(context, parseAuthorization -> {
            if (parseAuthorization.failed()) {
                handler.handle(Future.failedFuture(parseAuthorization.cause()));
                return;
            }

            handler.handle(Future.succeededFuture(parseAuthorization.result()));
        });
    }

    protected abstract void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler);
}
