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

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.ErrorInfo;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.LOGGER;
import static io.gravitee.am.gateway.handler.root.RootProvider.PATH_LOGIN_CALLBACK;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isHybridFlow;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isImplicitFlow;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackFailureHandler extends LoginAbstractHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackFailureHandler.class);
    private final Domain domain;
    private final AuthenticationFlowContextService authenticationFlowContextService;
    private final IdentityProviderManager identityProviderManager;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LoginCallbackFailureHandler(Domain domain,
                                       AuthenticationFlowContextService authenticationFlowContextService,
                                       IdentityProviderManager identityProviderManager,
                                       JWTService jwtService,
                                       CertificateManager certificateManager) {
        this.domain = domain;
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.identityProviderManager = identityProviderManager;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception
                    || throwable instanceof AbstractManagementException
                    || throwable instanceof AuthenticationException
                    || throwable instanceof PolicyChainException) {
                redirect(routingContext, throwable);
            } else {
                logger.error(throwable.getMessage(), throwable);
                if (routingContext.statusCode() != -1) {
                    routingContext
                            .response()
                            .setStatusCode(routingContext.statusCode())
                            .end();
                } else {
                    routingContext
                            .response()
                            .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                            .end();
                }
            }
        }
    }

    private void redirect(RoutingContext context, Throwable throwable) {
        try {


            Authentication authentication;
            // logout user if exists
            if (context.user() != null) {
                // prepare the authentication object to be able to
                // log out from IDP after the AM session clean up
                final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) context.user().getDelegate()).getUser();
                authentication = new EndUserAuthentication(endUser, null, new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate())));

                final String protocol = context.session().get(ConstantKeys.PROTOCOL_KEY);
                if (ConstantKeys.PROTOCOL_VALUE_SAML_REDIRECT.equals(protocol)) {
                    final var returnUrl = context.session().get(ConstantKeys.RETURN_URL_KEY);
                    clearSession(context, true);
                    context.session().put(RETURN_URL_KEY, returnUrl);
                    context.session().put(PROTOCOL_KEY, protocol);
                } else if (ConstantKeys.PROTOCOL_VALUE_SAML_POST.equals(protocol)) {
                    final var returnUrl = context.session().get(RETURN_URL_KEY);
                    final var transactionId = context.session().get(ConstantKeys.TRANSACTION_ID_KEY);
                    clearSession(context, false);
                    context.session().put(ConstantKeys.TRANSACTION_ID_KEY, transactionId);
                    context.session().put(RETURN_URL_KEY, returnUrl);
                    context.session().put(PROTOCOL_KEY, protocol);
                } else {
                    clearSession(context, true);
                }
            } else {
                authentication = new EndUserAuthentication(null, null, new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate())));
            }

            // redirect the user to either the login page or the SP redirect uri if hide login option is enabled
            final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final MultiMap originalParams = context.get(PARAM_CONTEXT_KEY);
            final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);

            var elContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate(), originalParams), context.data());
            var templateEngine = elContext.getTemplateEngine();

            // if client has activated hide login form and has only one active external IdP
            // redirect to the SP redirect_uri to avoid an infinite loop between AM and the external IdP
            long externalIdentities = Optional.ofNullable(client.getIdentityProviders()).stream()
                    .flatMap(Collection::stream)
                    .map(idp -> identityProviderManager.getIdentityProvider(idp.getIdentity()))
                    .filter(idp -> idp != null && idp.isExternal())
                    .count();

            // if client hasn't activated hide login form but has external IdP with matching selection rule
            // redirect to the SP redirect_uri to avoid an infinite loop between AM and the external IdP
            long selectedExternalIdp = Optional.ofNullable(client.getIdentityProviders()).stream()
                    .flatMap(Collection::stream)
                    .filter(aidp -> evaluateIdPSelectionRule(aidp, identityProviderManager.getIdentityProvider(aidp.getIdentity()), templateEngine))
                    .map(aidp -> identityProviderManager.getIdentityProvider(aidp.getIdentity()))
                    .filter(idp -> idp != null && idp.isExternal())
                    .count();

            if ((loginSettings != null && loginSettings.isHideForm() && externalIdentities == 1) || selectedExternalIdp > 0) {
                redirectToSP(originalParams, client, context, authentication, throwable);
            } else {
                redirectToLoginPage(originalParams, client, context, authentication, throwable);
            }

        } catch (Exception ex) {
            logger.error("An error has occurred while redirecting to the login page", ex);
            context
                    .response()
                    .setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503)
                    .end();
        }
    }

    private void clearSession(RoutingContext context, boolean clearAuthFlow) {
        if (clearAuthFlow) {
            // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
            authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                    .doOnError((error) -> logger.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                    .subscribe();
        }
        context.clearUser();
        context.session().destroy();
    }

    private void redirectToSP(MultiMap originalParams,
                              Client client,
                              RoutingContext context,
                              Authentication authentication,
                              Throwable throwable) throws URISyntaxException {
        // Get the SP redirect_uri

        final String protocol = context.session() != null ? context.session().get(ConstantKeys.PROTOCOL_KEY) : null;
        final String samlEndpoint = ConstantKeys.PROTOCOL_VALUE_SAML_REDIRECT.equals(protocol) || ConstantKeys.PROTOCOL_VALUE_SAML_POST.equals(protocol) ? context.session().get(RETURN_URL_KEY) : null;
        final String clientRedirectUri = samlEndpoint != null ? samlEndpoint : (originalParams != null && originalParams.get(Parameters.REDIRECT_URI) != null) ?
                originalParams.get(Parameters.REDIRECT_URI) :
                client.getRedirectUris().get(0);

        // create final redirect uri
        final var error = new ErrorInfo("server_error", null,
                throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage(),
                originalParams != null ? originalParams.get(Parameters.STATE): null);

        boolean fragment = originalParams != null &&
                originalParams.get(Parameters.RESPONSE_TYPE) != null &&
                (isImplicitFlow(originalParams.get(Parameters.RESPONSE_TYPE)) || isHybridFlow(originalParams.get(Parameters.RESPONSE_TYPE)));

        var finalRedirectUri = UriBuilder.buildErrorRedirect(clientRedirectUri, error, fragment);

        closeRemoteSessionAndRedirect(context, authentication, finalRedirectUri);
    }

    private void closeRemoteSessionAndRedirect(RoutingContext context, Authentication authentication, String redirectUrl) {
        AuthenticationProvider authProvider = context.get(ConstantKeys.PROVIDER_CONTEXT_KEY);
        // the login process is done and we want to close the session after the authentication
        String idToken = context.get(OIDC_PROVIDER_ID_TOKEN_KEY);
        if (authProvider instanceof SocialAuthenticationProvider socialIdp && socialIdp.closeSessionAfterSignIn() == CloseSessionMode.REDIRECT && StringUtils.isNotEmpty(idToken)) {
            final var logoutRequest = socialIdp.signOutUrl(authentication);

            final var stateJwt = new JWT();

            stateJwt.put(CLAIM_TARGET, redirectUrl);
            stateJwt.put(CLAIM_PROVIDER_ID, context.get(ConstantKeys.PROVIDER_ID_PARAM_KEY));
            stateJwt.put(CLAIM_ISSUING_REASON, ISSUING_REASON_CLOSE_IDP_SESSION);
            stateJwt.put(CLAIM_STATUS, STATUS_FAILURE);

            jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider())
                    .flatMapMaybe(state ->
                            logoutRequest.map(req -> {
                                var callbackUri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + PATH_LOGIN_CALLBACK);
                                return UriBuilder.fromHttpUrl(req.getUri())
                                        .addParameter(io.gravitee.am.common.oidc.Parameters.ID_TOKEN_HINT, encodeURIComponent(idToken))
                                        .addParameter(Parameters.STATE, encodeURIComponent(state))
                                        .addParameter(io.gravitee.am.common.oidc.Parameters.POST_LOGOUT_REDIRECT_URI, encodeURIComponent(callbackUri))
                                        .buildString();
                            })
                    )
                    // if the Maybe is empty, we redirect the user to the original request
                    .switchIfEmpty(Maybe.just(redirectUrl))
                    .subscribe(
                            url -> {
                                LOGGER.debug("Call logout on provider '{}'", (String) context.get(ConstantKeys.PROVIDER_ID_PARAM_KEY));
                                doRedirect(context, url);
                            },
                            err -> {
                                LOGGER.error("Session can't be closed on provider '{}'", context.get(ConstantKeys.PROVIDER_ID_PARAM_KEY), err);
                                doRedirect(context, redirectUrl);
                            });
        } else {
            // the login process is done
            // redirect the user to the original request
            doRedirect(context, redirectUrl);
        }
    }

    private void redirectToLoginPage(MultiMap originalParams,
                                     Client client,
                                     RoutingContext context,
                                     Authentication authentication,
                                     Throwable throwable) {
        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        if (originalParams != null) {
            params.setAll(originalParams);
        }
        params.set(Parameters.CLIENT_ID, client.getClientId());
        params.set(ConstantKeys.ERROR_PARAM_KEY, "social_authentication_failed");
        params.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, encodeURIComponent(throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage()));
        String uri = getUri(context, params);
        closeRemoteSessionAndRedirect(context, authentication, uri);
    }

    private String getUri(RoutingContext context, MultiMap params) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        final String replacement = loginSettings != null && loginSettings.isIdentifierFirstEnabled() ? "/identifier" : "";
        final String path = context.request().path().replaceFirst("/callback", replacement);
        return UriBuilderRequest.resolveProxyRequest(context.request(), path, params);
    }
}
