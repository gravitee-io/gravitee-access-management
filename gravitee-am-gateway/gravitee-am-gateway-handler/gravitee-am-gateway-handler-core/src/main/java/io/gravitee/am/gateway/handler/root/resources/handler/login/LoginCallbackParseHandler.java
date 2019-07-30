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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.exception.oauth2.BadClientCredentialsException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.RedirectAuthHandler;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackParseHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackParseHandler.class);
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String CLIENT_CONTEXT_KEY = "client";
    private ClientSyncService clientSyncService;
    private IdentityProviderManager identityProviderManager;

    public LoginCallbackParseHandler(ClientSyncService clientSyncService, IdentityProviderManager identityProviderManager) {
        this.clientSyncService = clientSyncService;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext context) {
        // fetch client (required for the next steps)
        parseClient(context, clientHandler -> {
            if (clientHandler.failed()) {
                context.fail(clientHandler.cause());
                return;
            }

            // set client in the execution context
            Client client = clientHandler.result();
            context.put(CLIENT_CONTEXT_KEY, client);

            // fetch social provider
            parseSocialProvider(context, socialProviderHandler -> {
                if (socialProviderHandler.failed()) {
                    context.fail(socialProviderHandler.cause());
                    return;
                }

                // set social provider in the execution context
                AuthenticationProvider authenticationProvider = socialProviderHandler.result();
                context.put(PROVIDER_PARAMETER, authenticationProvider);

                // continue
                context.next();
            });
        });
    }

    private void parseClient(RoutingContext context, Handler<AsyncResult<Client>> handler) {
        try {
            final String clientId = getQueryParams(context.session().get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM)).get(Parameters.CLIENT_ID);
            clientSyncService.findByClientId(clientId)
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            ex -> {
                                logger.error("An error occurs while getting client {}", clientId, ex);
                                handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                            },
                            () -> {
                                logger.error("Unknown client {}", clientId);
                                handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                            }
                    );

        } catch (Exception e) {
            logger.error("Failed to retrieve the initial client for the social authentication", e);
            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
        }
    }

    private void parseSocialProvider(RoutingContext context, Handler<AsyncResult<AuthenticationProvider>> handler) {
        final String providerId = context.request().getParam(PROVIDER_PARAMETER);

        if (providerId != null) {
            identityProviderManager.get(providerId)
                    .subscribe(
                            authenticationProvider -> handler.handle(Future.succeededFuture(authenticationProvider)),
                            ex -> {
                                logger.error("An error occurs while getting identity provider {}", providerId, ex);
                                handler.handle(Future.failedFuture(ex));
                            },
                            () -> {
                                logger.error("Unknown identity provider {}", providerId);
                                handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                            });
        } else {
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing provider parameter")));
        }
    }

    private Map<String, String> getQueryParams(String url) throws URISyntaxException {
        URI uri = UriBuilder.fromHttpUrl(url).build();
        return getParams(uri.getQuery());
    }

    private Map<String, String> getParams(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }
}
