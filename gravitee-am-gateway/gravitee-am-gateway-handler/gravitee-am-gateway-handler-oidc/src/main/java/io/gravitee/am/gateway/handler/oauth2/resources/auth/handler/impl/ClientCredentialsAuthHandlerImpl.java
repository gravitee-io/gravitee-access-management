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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl.AuthHandlerImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsAuthHandlerImpl extends AuthHandlerImpl {

    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";

    public ClientCredentialsAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }

    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        String clientId = context.request().getParam(Parameters.CLIENT_ID);
        String clientSecret = context.request().getParam(Parameters.CLIENT_SECRET);

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
