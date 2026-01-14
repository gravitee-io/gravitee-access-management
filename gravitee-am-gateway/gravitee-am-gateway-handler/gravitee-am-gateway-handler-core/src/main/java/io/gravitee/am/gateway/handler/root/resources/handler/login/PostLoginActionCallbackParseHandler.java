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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.STATE;

/**
 * Handler that parses and verifies the callback from the external post login action service.
 * Verifies AM's state JWT signature and restores context.
 *
 * @author GraviteeSource Team
 */
public class PostLoginActionCallbackParseHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PostLoginActionCallbackParseHandler.class);

    public static final String POST_LOGIN_ACTION_STATE_KEY = "postLoginActionState";
    public static final String POST_LOGIN_ACTION_RESPONSE_TOKEN_KEY = "postLoginActionResponseToken";

    private final ClientSyncService clientSyncService;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;
    private final Domain domain;

    public PostLoginActionCallbackParseHandler(ClientSyncService clientSyncService,
                                                JWTService jwtService,
                                                CertificateManager certificateManager,
                                                Domain domain) {
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // Extract state parameter (AM's signed JWT)
        String state = context.request().getParam("state");
        if (state == null || state.isEmpty()) {
            logger.error("Missing state parameter in post login action callback");
            context.fail(new InvalidRequestException("Missing state parameter"));
            return;
        }

        // Extract response token (external service's signed JWT)
        String responseToken = context.request().getParam("token");
        if (responseToken == null || responseToken.isEmpty()) {
            logger.error("Missing response token in post login action callback");
            context.fail(new InvalidRequestException("Missing response token"));
            return;
        }

        jwtService.decode(state, STATE)
                .flatMap(stateJwt -> {
                    String clientId = (String) stateJwt.get(PostLoginActionHandler.CLAIM_CLIENT_ID);
                    if (clientId == null || clientId.isEmpty()) {
                        logger.error("Missing client ID in state token");
                        return io.reactivex.rxjava3.core.Single.error(new InvalidRequestException("Invalid state token"));
                    }
                    return clientSyncService.findByClientId(clientId)
                            .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(new InvalidRequestException("Client not found")))
                            .flatMap(client -> {
                                PostLoginAction settings = PostLoginAction.getInstance(domain, client);
                                return resolveCertificateProvider(settings)
                                        .flatMap(provider -> jwtService.decodeAndVerify(state, provider, STATE))
                                        .map(verifiedJwt -> new VerifiedState(verifiedJwt, client));
                            });
                })
                .subscribe(
                        result -> {
                            JWT stateJwt = result.stateJwt;
                            Client client = result.client;

                            Long exp = stateJwt.getExp();
                            if (exp != null && Instant.now().getEpochSecond() > exp) {
                                logger.error("State token expired");
                                context.fail(new InvalidRequestException("State token expired"));
                                return;
                            }

                            context.put(POST_LOGIN_ACTION_STATE_KEY, stateJwt);
                            context.put(POST_LOGIN_ACTION_RESPONSE_TOKEN_KEY, responseToken);
                            context.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                            context.next();
                        },
                        error -> {
                            logger.error("Failed to verify state JWT", error);
                            context.fail(new InvalidRequestException("Invalid state token"));
                        }
                );
    }

    private io.reactivex.rxjava3.core.Single<io.gravitee.am.gateway.certificate.CertificateProvider> resolveCertificateProvider(PostLoginAction settings) {
        String certificateId = settings != null ? settings.getCertificateId() : null;
        if (certificateId == null || certificateId.isEmpty()) {
            return io.reactivex.rxjava3.core.Single.just(certificateManager.defaultCertificateProvider());
        }
        return certificateManager.get(certificateId)
                .switchIfEmpty(io.reactivex.rxjava3.core.Single.just(certificateManager.defaultCertificateProvider()));
    }

    private static final class VerifiedState {
        private final JWT stateJwt;
        private final Client client;

        private VerifiedState(JWT stateJwt, Client client) {
            this.stateJwt = stateJwt;
            this.client = client;
        }
    }
}
