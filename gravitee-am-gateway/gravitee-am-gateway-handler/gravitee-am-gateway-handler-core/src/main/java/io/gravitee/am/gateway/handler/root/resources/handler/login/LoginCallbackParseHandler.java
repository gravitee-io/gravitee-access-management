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

import io.gravitee.am.common.crypto.CryptoUtils;
import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.common.exception.oauth2.BadClientCredentialsException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.security.Key;

import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_ISSUING_REASON;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_PROVIDER_ID;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_QUERY_PARAM;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_REMEMBER_ME;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_TARGET;
import static io.gravitee.am.common.utils.ConstantKeys.ISSUING_REASON_CLOSE_IDP_SESSION;
import static io.gravitee.am.common.utils.ConstantKeys.PROTOCOL_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROTOCOL_VALUE_SAML_POST;
import static io.gravitee.am.common.utils.ConstantKeys.RETURN_URL_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.STATE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackParseHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackParseHandler.class);
    private static final String RELAY_STATE_PARAM_KEY = "RelayState";

    private final ClientSyncService clientSyncService;
    private final IdentityProviderManager identityProviderManager;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LoginCallbackParseHandler(ClientSyncService clientSyncService, IdentityProviderManager identityProviderManager, JWTService jwtService, CertificateManager certificateManager) {
        this.clientSyncService = clientSyncService;
        this.identityProviderManager = identityProviderManager;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext context) {
        // First, restore the initial query parameters (those provided when accessing /oauth/authorize on AM side).
        String error = context.get(ConstantKeys.ERROR_PARAM_KEY);
        if (error != null) {
            context.fail(new LoginCallbackFailedException(error));
            return;
        }
        restoreInitialQueryParams(context, next -> {

            if (next.failed()) {
                context.fail(next.cause());
                return;
            }

            if (!context.get(ConstantKeys.CONTINUE_CALLBACK_PROCESSING, true)) {
                logger.debug("Login Callback called by IDP post logout redirection");
                String redirectUrl = context.get(ConstantKeys.RETURN_URL_KEY);
                context.response()
                        .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUrl)
                        .setStatusCode(302)
                        .end();
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

                // fetch social provider
                parseSocialProvider(context, socialProviderHandler -> {
                    if (socialProviderHandler.failed()) {
                        context.fail(socialProviderHandler.cause());
                        return;
                    }

                    // set social provider in the execution context
                    AuthenticationProvider authenticationProvider = socialProviderHandler.result();
                    context.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);

                    // continue
                    context.next();
                });
            });
        });
    }

    private void restoreInitialQueryParams(RoutingContext context, Handler<AsyncResult<Boolean>> handler) {

        String state = context.request().getParam(Parameters.STATE);
        if (state == null) {
            state = context.request().getParam(RELAY_STATE_PARAM_KEY);
            if (state == null) {
                state = context.get(Parameters.STATE);
            }
        }
        if (state.isEmpty()) {
            logger.error("No state or RelayState on login callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing state query param")));
            return;
        }
        var stateJwtCertProvider = certificateManager.defaultCertificateProvider();
        jwtService.decodeAndVerify(state, stateJwtCertProvider, STATE)
                .flatMap(stateJwt -> extractIdpCodeVerifier(stateJwt, stateJwtCertProvider)
                        .map(codeVerifier -> {
                            context.put(ConstantKeys.IDP_CODE_VERIFIER, codeVerifier);
                            return stateJwt;
                        }).defaultIfEmpty(stateJwt))
                .doOnSuccess(stateJwt -> {
                    final MultiMap initialQueryParams = RequestUtils.getQueryParams((String) stateJwt.getOrDefault(CLAIM_QUERY_PARAM, ""), false);
                    context.put(ConstantKeys.PARAM_CONTEXT_KEY, initialQueryParams);
                    context.put(ConstantKeys.PROVIDER_ID_PARAM_KEY, stateJwt.get(CLAIM_PROVIDER_ID));
                    context.put(ConstantKeys.REMEMBER_ME_PARAM_KEY, stateJwt.get(CLAIM_REMEMBER_ME));

                    if (ISSUING_REASON_CLOSE_IDP_SESSION.equals(stateJwt.get(CLAIM_ISSUING_REASON))) {
                        context.put(ConstantKeys.CONTINUE_CALLBACK_PROCESSING, false);
                        context.put(ConstantKeys.RETURN_URL_KEY, stateJwt.get(CLAIM_TARGET));
                    } else {
                        context.put(ConstantKeys.CONTINUE_CALLBACK_PROCESSING, true);
                    }

                    final String protocol = (String) stateJwt.get(PROTOCOL_KEY);
                    if (StringUtils.hasLength(protocol)) {
                        // SAML flow, need to restore these session attributes
                        // in order to redirect the process to the SAML Handler and
                        // not onto OAuth2 flow
                        context.session().put(PROTOCOL_KEY, protocol);
                        context.session().put(RETURN_URL_KEY, stateJwt.get(RETURN_URL_KEY));
                        if (PROTOCOL_VALUE_SAML_POST.equals(protocol)) {
                            context.session().put(TRANSACTION_ID_KEY, stateJwt.get(TRANSACTION_ID_KEY));
                        }
                    }
                })
                .subscribe(
                        stateJwt -> handler.handle(Future.succeededFuture(true)),
                        ex -> {
                            logger.error("An error occurs verifying state on login callback", ex);
                            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                        });
    }

    private Maybe<String> extractIdpCodeVerifier(JWT stateJwt, CertificateProvider stateJwtCertProvider) {
        return stateJwtCertProvider.getProvider()
                .key()
                .flatMapMaybe(k -> {
                    var ecv = (String) stateJwt.get(Claims.ENCRYPTED_CODE_VERIFIER);
                    if (!StringUtils.hasText(ecv)) {
                        return Maybe.empty();
                    }
                    return Maybe.just(CryptoUtils.decrypt(ecv, (Key) k.getValue()));
                });
    }

    private void parseClient(RoutingContext context, Handler<AsyncResult<Client>> handler) {

        if (context.get(ConstantKeys.PARAM_CONTEXT_KEY) == null || !(context.get(ConstantKeys.PARAM_CONTEXT_KEY) instanceof MultiMap)) {
            logger.error("Unable to restore initial client query params login callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing client query parameters")));
            return;
        }

        final String clientId = ((MultiMap) context.get(ConstantKeys.PARAM_CONTEXT_KEY)).get(Parameters.CLIENT_ID);
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

    private void parseSocialProvider(RoutingContext context, Handler<AsyncResult<AuthenticationProvider>> handler) {

        final String providerId = context.get(ConstantKeys.PROVIDER_ID_PARAM_KEY);

        if (!StringUtils.hasText(providerId)) {
            logger.error("No provider identifier on login callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing provider id")));
            return;
        }

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
    }
}
