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
package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuthenticationProvider implements AuthProvider {

    private final Logger logger = LoggerFactory.getLogger(ClientAuthenticationProvider.class);

    private ClientService clientService;

    public ClientAuthenticationProvider() {}

    public ClientAuthenticationProvider(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> authHandler) {
        String clientId = credentials.getString(OAuth2Constants.CLIENT_ID);
        String clientSecret = credentials.getString(OAuth2Constants.CLIENT_SECRET);

        logger.debug("Trying to authenticate a client: clientId[{}]", clientId);

        clientService
                .findByClientId(clientId)
                .subscribe(
                        client -> {
                            if (client.getClientSecret().equals(clientSecret)) {
                                authHandler.handle(Future.succeededFuture(new Client(client.getClientId())));
                            } else {
                                authHandler.handle(Future.failedFuture(new BadClientCredentialsException()));
                            }
                        },
                        error -> {
                            logger.error("Unexpected error while looking for a client: clientId[{}]", clientId, error);
                            authHandler.handle(Future.failedFuture(error));
                        },
                        () -> authHandler.handle(Future.failedFuture(new BadClientCredentialsException()))
                );
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }
}
