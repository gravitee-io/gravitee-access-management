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
package io.gravitee.am.gateway.handler.root.resources.handler.logout;

import io.gravitee.am.common.exception.oauth2.BadClientCredentialsException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutCallbackParseHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LogoutCallbackParseHandler.class);

    private final ClientSyncService clientSyncService;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LogoutCallbackParseHandler(ClientSyncService clientSyncService, JWTService jwtService, CertificateManager certificateManager) {
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext context) {

        // First, restore the initial query parameters (those provided when accessing /oauth/authorize on AM side).
        restoreState(context, next -> {

            if (next.failed()) {
                context.fail(next.cause());
                return;
            }

            // fetch client (required for the next steps)
            parseClient(context, clientHandler -> {
                if (clientHandler.failed()) {
                    context.fail(clientHandler.cause());
                    return;
                }

                // set client in the execution context
                Client client = clientHandler.result();
                context.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                context.next();
            });
        });
    }

    private void restoreState(RoutingContext context, Handler<AsyncResult<Boolean>> handler) {
        final String state = context.request().getParam(Parameters.STATE);

        if (StringUtils.isEmpty(state)) {
            logger.error("No state on login callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing state query param")));
            return;
        }

        jwtService.decodeAndVerify(state, certificateManager.defaultCertificateProvider())
                .doOnSuccess(stateJwt -> {
                    final MultiMap initialQueryParams = RequestUtils.getQueryParams((String) stateJwt.getOrDefault("q", ""), false);
                    context.put(ConstantKeys.PARAM_CONTEXT_KEY, initialQueryParams);
                    context.put(ConstantKeys.PROVIDER_ID_PARAM_KEY, stateJwt.get("p"));
                    context.put(Parameters.CLIENT_ID, stateJwt.get("c"));
                })
                .subscribe(
                        stateJwt -> handler.handle(Future.succeededFuture(true)),
                        ex -> {
                            logger.error("An error occurs verifying state on login callback", ex);
                            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                        });
    }

    private void parseClient(RoutingContext context, Handler<AsyncResult<Client>> handler) {

        if (context.get(Parameters.CLIENT_ID) == null) {
            logger.error("Unable to restore client for logout callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Invalid state")));
            return;
        }

        final String clientId = context.get(Parameters.CLIENT_ID);
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
    }
}
