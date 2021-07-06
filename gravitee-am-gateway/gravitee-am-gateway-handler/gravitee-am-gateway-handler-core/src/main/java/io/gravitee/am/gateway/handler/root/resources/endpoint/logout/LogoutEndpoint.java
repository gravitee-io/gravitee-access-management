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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpoint extends AbstractLogoutEndpoint {
    private IdentityProviderManager identityProviderManager;
    private CertificateManager certificateManager;
    private ClientSyncService clientSyncService;
    private JWTService jwtService;

    public LogoutEndpoint(Domain domain,
                          TokenService tokenService,
                          AuditService auditService,
                          ClientSyncService clientSyncService,
                          JWTService jwtService,
                          AuthenticationFlowContextService authenticationFlowContextService,
                          IdentityProviderManager identityProviderManager,
                          CertificateManager certificateManager) {
        super(domain, tokenService, auditService, authenticationFlowContextService);
        this.jwtService = jwtService;
        this.clientSyncService = clientSyncService;
        this.certificateManager = certificateManager;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {

        // fetch client
        fetchClient(routingContext, clientHandler -> {
            final Client client = clientHandler.result();

            // put client in context for later use
            if (client != null) {
                Client safeClient = new Client(client);
                safeClient.setClientSecret(null);
                routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, safeClient);
            }

            evaluateSingleSignOut(routingContext, endpointHandler -> {
                Optional<String> oidcEndSessionEndpoint = endpointHandler.result();
                if (oidcEndSessionEndpoint.isPresent()) {
                    // redirect to the OIDC provider to logout the user
                    // this action will return to the AM logout callback to finally logout the user from AM
                    doRedirect(client, routingContext, oidcEndSessionEndpoint.get());
                } else {
                    // External OP do not provide EndSessionEndpoint, call the "standard" AM logout mechanism
                    // to invalidate session
                    invalidateSession(routingContext, invalidSessionHandler(routingContext, client));
                }
            } );
        });
    }

    /**
     * Get client for the current logout request.
     *
     * Client will be used to check the validity of the target_url to avoid potential open redirection.
     *
     * @param routingContext the routing context
     * @param handler handler holding the potential client
     */
    private void fetchClient(RoutingContext routingContext, Handler<AsyncResult<Client>> handler) {
        // The OP SHOULD accept ID Tokens when the RP identified by the ID Token's aud claim and/or sid claim has a current session
        // or had a recent session at the OP, even when the exp time has passed.
        if (routingContext.request().getParam(Parameters.ID_TOKEN_HINT) != null) {
            final String idToken = routingContext.request().getParam(Parameters.ID_TOKEN_HINT);
            jwtService.decode(idToken)
                    .flatMapMaybe(jwt -> clientSyncService.findByClientId(jwt.getAud()))
                    .flatMap(client -> jwtService.decodeAndVerify(idToken, client).toMaybe().map(__ -> client)
                            .onErrorResumeNext(ex -> (ex instanceof ExpiredJWTException) ? Maybe.just(client) : Maybe.error(ex)))
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.succeededFuture()),
                            () -> handler.handle(Future.succeededFuture()));
        } else {
            // if no user, continue
            if (routingContext.user() == null) {
                handler.handle(Future.succeededFuture());
                return;
            }
            // get client from the user's last application
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            clientSyncService.findById(endUser.getClient())
                    .switchIfEmpty(Maybe.defer(() -> clientSyncService.findByClientId(endUser.getClient())))
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.succeededFuture()),
                            () -> handler.handle(Future.succeededFuture()));
        }
    }

    private void evaluateSingleSignOut(RoutingContext routingContext, Handler<AsyncResult<Optional<String>>> handler) {
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (client != null && client.isSingleSignOut() && routingContext.user() != null) {
            User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // generate Authentication object containing Request and User information
            // maybe useful in some IDP to generate the Logout Request
            SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(routingContext.request().getDelegate()));
            final Authentication authentication = new EndUserAuthentication(endUser, null, authenticationContext);

            final Maybe<AuthenticationProvider> authenticationProviderMaybe = this.identityProviderManager.get(endUser.getSource());
            authenticationProviderMaybe
                    .filter(provider -> provider instanceof SocialAuthenticationProvider)
                    .flatMap(provider -> ((SocialAuthenticationProvider) provider).signOutUrl(authentication))
                    .map(request -> Optional.ofNullable(request))
                    .switchIfEmpty(Maybe.just(Optional.empty()))
                    .flatMap(optLogoutRequest  -> {
                        if (optLogoutRequest.isPresent()) {
                            return generateLogoutCallback(routingContext, endUser, optLogoutRequest.get());
                        } else {
                            LOGGER.debug("No logout endpoint has been found in the Identity Provider configuration");
                            return Maybe.just(Optional.<String>empty());
                        }
                    })
                    .doOnSuccess(endpoint -> handler.handle(Future.succeededFuture(endpoint)))
                    .doOnError(err -> {
                        LOGGER.warn("Unable to sign the end user out of the external OIDC '{}', only sign out of AM", client.getClientId(), err);
                        handler.handle(Future.succeededFuture(Optional.empty()));
                    })
                    .subscribe();
        } else {
            handler.handle(Future.succeededFuture(Optional.empty()));
        }
    }

    private Maybe<Optional<String>> generateLogoutCallback(RoutingContext routingContext, User endUser, Request endpoint) {
        // Case of OIDC provider
        // Single Logout can be done only if the endUser profile contains an IdToken.
        if (HttpMethod.GET == endpoint.getMethod() &&
                endUser.getAdditionalInformation() != null &&
                endUser.getAdditionalInformation().containsKey(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY)) {
            // Generate a state containing provider id and current query parameter string.
            // This state will be sent back to AM after social logout.
            final JWT stateJwt = new JWT();
            stateJwt.put("c", endUser.getClient());
            stateJwt.put("p", endUser.getSource());
            stateJwt.put("q", routingContext.request().query());
            // remove state from the request to avoid duplicate state parameter into the external idp logout request
            // this state will be restored during after the redirect triggered by the external idp
            routingContext.request().params().remove(io.gravitee.am.common.oauth2.Parameters.STATE);

            return jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider()).map(state -> {
                String redirectUri = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/logout/callback");
                UriBuilder builder = UriBuilder.fromHttpUrl(endpoint.getUri());
                builder.addParameter(Parameters.POST_LOGOUT_REDIRECT_URI, redirectUri);
                builder.addParameter(Parameters.ID_TOKEN_HINT, (String) endUser.getAdditionalInformation().get(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY));
                builder.addParameter(io.gravitee.am.common.oauth2.Parameters.STATE, state);
                return Optional.of(builder.buildString());
            }).toMaybe();
        } else {
            // other case not yet implemented, return empty to log out only of AM.
            return Maybe.empty();
        }
    }
}
