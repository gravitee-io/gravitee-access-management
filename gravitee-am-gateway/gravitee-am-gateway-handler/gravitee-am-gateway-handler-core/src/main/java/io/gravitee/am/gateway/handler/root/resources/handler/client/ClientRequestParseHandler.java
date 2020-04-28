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
package io.gravitee.am.gateway.handler.root.resources.handler.client;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientRequestParseHandler implements Handler<RoutingContext> {

    public static final String CLIENT_CONTEXT_KEY = "client";
    private ClientSyncService clientSyncService;
    private boolean required;

    public ClientRequestParseHandler(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String clientId = context.request().getParam(Parameters.CLIENT_ID);
        if (clientId == null || clientId.isEmpty()) {
            if (required) {
                throw new InvalidRequestException("Missing parameter: client_id is required");
            } else {
                context.next();
                return;
            }
        }

        authenticate(clientId, authHandler -> {
            if (authHandler.failed()) {
                context.fail(authHandler.cause());
                return;
            }

            Client safeClient = new Client(authHandler.result());
            safeClient.setClientSecret(null);
            context.put(CLIENT_CONTEXT_KEY, safeClient);
            context.next();
        });
    }

    public void setRequired(boolean required) {
        this.required = required;
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
