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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.gateway.handler.common.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthProviderImpl implements UserAuthProvider {

    private final static Logger logger = LoggerFactory.getLogger(UserAuthProviderImpl.class);
    private final static String USERNAME_PARAMETER = "username";
    private final static String PASSWORD_PARAMETER = "password";

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private ClientSyncService clientSyncService;

    @Override
    public void authenticate(RoutingContext context, JsonObject authInfo, Handler<AsyncResult<User>> handler) {
        String username = authInfo.getString(USERNAME_PARAMETER);
        String password = authInfo.getString(PASSWORD_PARAMETER);
        String clientId = authInfo.getString(Parameters.CLIENT_ID);
        String ipAddress = authInfo.getString(Claims.ip_address);
        String userAgent = authInfo.getString(Claims.user_agent);

        parseClient(clientId, parseClientHandler -> {
            if (parseClientHandler.failed()) {
                logger.error("Authentication failure: unable to retrieve client " + clientId, parseClientHandler.cause());
                handler.handle(Future.failedFuture(parseClientHandler.cause()));
                return;
            }

            // retrieve the client (application)
            final Client client = parseClientHandler.result();

            // end user authentication
            SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request()));
            final Authentication authentication = new EndUserAuthentication(username, password, authenticationContext);

            authenticationContext.set(Claims.ip_address, ipAddress);
            authenticationContext.set(Claims.user_agent, userAgent);
            authenticationContext.set(Claims.domain, client.getDomain());

            userAuthenticationManager.authenticate(client, authentication)
                    .subscribe(
                            user -> handler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user))),
                            error -> handler.handle(Future.failedFuture(error))
                    );
        });
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {
        authenticate(null, authInfo, handler);
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
