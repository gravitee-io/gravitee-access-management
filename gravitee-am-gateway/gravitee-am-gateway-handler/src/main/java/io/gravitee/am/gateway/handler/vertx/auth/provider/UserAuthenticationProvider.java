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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationProvider implements AuthProvider {

    private final static Logger logger = LoggerFactory.getLogger(UserAuthenticationProvider.class);
    private final static String USERNAME_PARAMETER = "username";
    private final static String PASSWORD_PARAMETER = "password";
    private UserAuthenticationManager userAuthenticationManager;
    private ClientSyncService clientSyncService;

    public UserAuthenticationProvider() {}

    public UserAuthenticationProvider(UserAuthenticationManager userAuthenticationManager, ClientSyncService clientSyncService) {
        this.userAuthenticationManager = userAuthenticationManager;
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        String username = authInfo.getString(USERNAME_PARAMETER);
        String password = authInfo.getString(PASSWORD_PARAMETER);
        String clientId = authInfo.getString(OAuth2Constants.CLIENT_ID);
        String ipAddress = authInfo.getString(Claims.ip_address);
        String userAgent = authInfo.getString(Claims.user_agent);

        parseClient(clientId, parseClientHandler -> {
            if (parseClientHandler.failed()) {
                logger.error("Authentication failure: unable to retrieve client " + clientId, parseClientHandler.cause());
                resultHandler.handle(Future.failedFuture(parseClientHandler.cause()));
                return;
            }

            final Client client = parseClientHandler.result();

            final Authentication authentication = new EndUserAuthentication(username, password);
            Map<String, Object> additionalInformation = new HashMap();
            additionalInformation.put(Claims.ip_address, ipAddress);
            additionalInformation.put(Claims.user_agent, userAgent);
            additionalInformation.put(Claims.domain, client.getDomain());
            ((EndUserAuthentication) authentication).setAdditionalInformation(additionalInformation);

            userAuthenticationManager.authenticate(client, authentication)
                    .subscribe(
                            user -> resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.vertx.auth.user.User(user))),
                            error -> resultHandler.handle(Future.failedFuture(error))
                    );
        });
    }

    private void parseClient(String clientId, Handler<AsyncResult<Client>> authHandler) {
        logger.debug("Attempt authentication with client " + clientId);

        clientSyncService
                .findByClientId(clientId)
                .subscribe(
                        client -> authHandler.handle(Future.succeededFuture(client)),
                        error -> authHandler.handle(Future.failedFuture(new ServerErrorException("Server error: unable to find client with client_id " + clientId))),
                        () -> authHandler.handle(Future.failedFuture(new InvalidRequestException("No client found for client_id " + clientId)))
                );
    }
}
