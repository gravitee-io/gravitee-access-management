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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentPrepareContextHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String USER_CONTEXT_KEY = "user";
    private static final String AUTHORIZATION_REQUEST_CONTEXT_KEY = "authorizationRequest";
    private static final String ID_TOKEN_SESSION_CONTEXT_KEY = "id_token";
    private static final String ID_TOKEN_CONTEXT_KEY = "idToken";
    private ClientSyncService clientSyncService;

    public UserConsentPrepareContextHandler(ClientSyncService clientSyncService) {
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

            // check user
            User authenticatedUser = routingContext.user();
            if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
                routingContext.fail(new AccessDeniedException());
                return;
            }

            // prepare context
            Client safeClient = new Client(resultHandler.result());
            safeClient.setClientSecret(null);
            io.gravitee.am.model.User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();
            prepareContext(routingContext, safeClient, user, authorizationRequest);

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

    private void prepareContext(RoutingContext context, Client client, io.gravitee.am.model.User user, AuthorizationRequest authorizationRequest) {
        context.put(CLIENT_CONTEXT_KEY, client);
        context.put(USER_CONTEXT_KEY, user);
        context.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);

        // add id_token if exists
        String idToken = context.session().get(ID_TOKEN_SESSION_CONTEXT_KEY);
        if (idToken != null) {
            context.put(ID_TOKEN_CONTEXT_KEY, idToken);
        }
    }
}
