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
package io.gravitee.am.gateway.handler.vertx.handler.login.endpoint;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Login page must be call after user being redirected here from restricted endpoint (i.e Authorization Endpoint)
 * Login request must also have client_id parameter to fetch the related identity providers
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginRequestParseHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private ClientService clientService;

    public LoginRequestParseHandler(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void handle(RoutingContext context) {
        Session session = context.session();
        if (session == null || session.get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM) == null) {
            throw new InvalidRequestException("User cannot log in directly from the login page");
        }

        final String clientId = context.request().getParam(OAuth2Constants.CLIENT_ID);
        if (clientId == null || clientId.isEmpty()) {
            throw new InvalidRequestException("Missing parameter: client_id is required");
        }

        authenticate(clientId, authHandler -> {
            if (authHandler.failed()) {
                context.fail(authHandler.cause());
                return;
            }

            context.put(CLIENT_CONTEXT_KEY, authHandler.result());
            context.next();
        });
    }

    private void authenticate(String clientId, Handler<AsyncResult<Client>> authHandler) {
        clientService
                .findByClientId(clientId)
                .subscribe(
                        client -> authHandler.handle(Future.succeededFuture(client)),
                        error -> authHandler.handle(Future.failedFuture(new ServerErrorException("Server error: unable to find client with client_id " + clientId))),
                        () -> authHandler.handle(Future.failedFuture(new InvalidRequestException("No client found for client_id " + clientId)))
                );
    }
}
