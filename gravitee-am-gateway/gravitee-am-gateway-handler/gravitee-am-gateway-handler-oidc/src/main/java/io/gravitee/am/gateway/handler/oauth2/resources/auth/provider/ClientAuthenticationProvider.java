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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.user.Client;
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
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";

    private ClientSyncService clientSyncService;

    public ClientAuthenticationProvider() {}

    public ClientAuthenticationProvider(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> authHandler) {
        String clientId = credentials.getString(USERNAME_FIELD);
        String clientSecret = credentials.getString(PASSWORD_FIELD);

        logger.debug("Trying to authenticate a client: clientId[{}]", clientId);

        clientSyncService
                .findByClientId(clientId)
                .subscribe(
                        client -> {
                            if (client.getClientSecret().equals(clientSecret)) {
                                authHandler.handle(Future.succeededFuture(new Client(client)));
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

    public void setClientSyncService(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }
}
