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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientRequestParseHandler implements Handler<RoutingContext> {

    private final ClientSyncService clientSyncService;
    private boolean required;
    private boolean continueOnError;

    public ClientRequestParseHandler(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String clientId = Optional.ofNullable(context.request().getParam(Parameters.CLIENT_ID))
                .orElseGet(() -> context.get(Parameters.CLIENT_ID));
        if (clientId == null || clientId.isEmpty()) {
            if (required) {
                context.fail(new InvalidRequestException("Missing parameter: client_id is required"));
            } else {
                context.next();
            }
            return;
        }

        authenticate(clientId, authHandler -> {
            if (authHandler.failed()) {
                if (continueOnError) {
                    context.next();
                } else {
                    context.fail(authHandler.cause());
                }
                return;
            }

            context.put(CLIENT_CONTEXT_KEY, authHandler.result().asSafeClient());
            context.next();
        });
    }

    public ClientRequestParseHandler setRequired(boolean required) {
        this.required = required;
        return this;
    }

    public ClientRequestParseHandler setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
        return this;
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
