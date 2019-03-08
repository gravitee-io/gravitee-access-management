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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.approval;

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserApprovalRequestParseHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private ClientSyncService clientSyncService;

    public UserApprovalRequestParseHandler(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // user must redirected here after an authorization request
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);
        if (authorizationRequest == null) {
            routingContext.response().setStatusCode(400).end("An authorization request is required to handle user approval");
            return;
        }

        // check client
        authenticate(authorizationRequest.getClientId(), resultHandler -> {
            if (resultHandler.failed()) {
                routingContext.fail(resultHandler.cause());
                return;
            }
            // put client in the execution context
            Client client = resultHandler.result();
            routingContext.put(CLIENT_CONTEXT_KEY, client);

            // check user
            User authenticatedUser = routingContext.user();
            if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.vertx.auth.user.User)) {
                routingContext.fail(new AccessDeniedException());
                return;
            }
            routingContext.next();
        });

    }


    private void authenticate(String clientId, Handler<AsyncResult<Client>> authHandler) {
        clientSyncService
                .findByClientId(clientId)
                .subscribe(
                        client -> authHandler.handle(Future.succeededFuture(client)),
                        error -> authHandler.handle(Future.failedFuture(new ServerErrorException("Server error: unable to find client with client_id " + clientId))),
                        () -> authHandler.handle(Future.failedFuture(new InvalidRequestException("No client found for client_id " + clientId)))
                );
    }
}
