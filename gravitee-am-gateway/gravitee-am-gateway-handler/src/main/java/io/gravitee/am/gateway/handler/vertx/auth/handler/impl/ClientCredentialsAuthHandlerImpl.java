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

import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsAuthHandlerImpl extends AuthHandlerImpl {

    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";

    public ClientCredentialsAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }

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

    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        String clientId = context.request().getParam(OAuth2Constants.CLIENT_ID);
        String clientSecret = context.request().getParam(OAuth2Constants.CLIENT_SECRET);

        if (clientId != null && clientSecret != null) {
            JsonObject clientCredentials = new JsonObject()
                    .put(USERNAME_FIELD, clientId)
                    .put(PASSWORD_FIELD, clientSecret);
            handler.handle(Future.succeededFuture(clientCredentials));
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }
}
