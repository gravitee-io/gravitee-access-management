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

import com.google.common.net.HttpHeaders;
import io.gravitee.am.common.exception.authentication.AccountDeviceIntegrityException;
import io.gravitee.am.common.exception.authentication.AccountEnforcePasswordException;
import io.gravitee.am.common.exception.authentication.AccountPasswordExpiredException;
import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.ErrorInfo;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_FAILED;
import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROTOCOL_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RETURN_URL_KEY;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isHybridFlow;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isImplicitFlow;
import static java.util.Objects.nonNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginFailureHandler extends LoginAbstractHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final AuthenticationFlowContextService authenticationFlowContextService;
    private final Domain domain;
    private final IdentityProviderManager identityProviderManager;

    public LoginFailureHandler(AuthenticationFlowContextService authenticationFlowContextService,
                               Domain domain, IdentityProviderManager identityProviderManager) {
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.domain = domain;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof PolicyChainException policyChainException) {
                handlePolicyChainException(routingContext, policyChainException.key(), policyChainException.getMessage());
            } else if (throwable instanceof AccountPasswordExpiredException) {
                handleException(routingContext, ((AccountPasswordExpiredException) throwable).getErrorCode(), throwable.getMessage());
            } else if (throwable instanceof AccountEnforcePasswordException) {
                handleException(routingContext, ((AccountEnforcePasswordException) throwable).getErrorCode(), throwable.getMessage());
            } else if (throwable instanceof AccountDeviceIntegrityException) {
                handleException(routingContext, ((AccountDeviceIntegrityException) throwable).getErrorCode(), throwable.getMessage());
            } else if (throwable instanceof AuthenticationException) {
                handleException(routingContext, "invalid_user", "Invalid or unknown user");
            } else {
                // technical exception will be managed by the generic error handler, continue
                routingContext.next();
            }
        }
    }

    private void handlePolicyChainException(RoutingContext context, String errorCode, String errorDescription) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final long externalIdentities = Optional.ofNullable(client.getIdentityProviders()).stream()
                .flatMap(Collection::stream)
                .map(idp -> identityProviderManager.getIdentityProvider(idp.getIdentity()))
                .filter(idp -> idp != null && idp.isExternal())
                .count();

        final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        if (loginSettings != null && loginSettings.isHideForm() && externalIdentities == 1) {
            logoutUser(context);
            final MultiMap originalParams = context.get(PARAM_CONTEXT_KEY);
            redirectToCallbackUrl(originalParams, client, context, errorCode, errorDescription);
        } else {
            handleException(context, errorCode, errorDescription);
        }
    }

    private void redirectToCallbackUrl(MultiMap originalParams, Client client, RoutingContext context,
                                       String errorCode, String errorDescription) {
        // Get the SP redirect_uri
        final String clientRedirectUri = (originalParams != null && originalParams.get(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI) != null) ?
                originalParams.get(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI) :
                client.getRedirectUris().get(0);

        final var error = new ErrorInfo("login_failed", errorCode, errorDescription, originalParams == null ? null : originalParams.get(io.gravitee.am.common.oauth2.Parameters.STATE));
        final boolean fragment = originalParams != null &&
                originalParams.get(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE) != null &&
                (isImplicitFlow(originalParams.get(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE)) || isHybridFlow(originalParams.get(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE)));

        // get URI from the redirect_uri parameter
        try {
            final var finalRedirectUri = UriBuilder.buildErrorRedirect(clientRedirectUri, error, fragment);
            doRedirect(context, finalRedirectUri);
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while redirecting to the login page", ex);
            context
                    .response()
                    .setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503)
                    .end();
        }
    }

    private void handleException(RoutingContext context, String errorCode, String errorDescription) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        logoutUser(context);

        // redirect to the login page with error message
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(req);
        // add error messages
        queryParams.set(ConstantKeys.ERROR_PARAM_KEY, LOGIN_FAILED);
        StringBuilder error = new StringBuilder();
        error.append(LOGIN_FAILED);
        if (errorCode != null) {
            queryParams.set(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode);
        }
        if (errorDescription != null) {
            queryParams.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
            error.append("$");
            error.append(errorDescription);
        }

        if (context.session() != null) {
            String hash = HashUtil.generateSHA256(error.toString());
            context.session().put(ERROR_HASH, hash);
        }

        if (nonNull(context.request().getParam(Parameters.LOGIN_HINT)) && nonNull(context.request().getParam(ConstantKeys.USERNAME_PARAM_KEY))) {
            // encode login_hint parameter (to not replace '+' sign by a space ' ')
            queryParams.set(Parameters.LOGIN_HINT, StaticEnvironmentProvider.sanitizeParametersEncoding() ?
                    UriBuilder.encodeURIComponent(context.request().getParam(ConstantKeys.USERNAME_PARAM_KEY)) : context.request().getParam(ConstantKeys.USERNAME_PARAM_KEY));
        }
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), queryParams, true);
        doRedirect(resp, uri);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }

    private void logoutUser(RoutingContext context) {
        if (context.user() != null) {
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
        }
    }

    private void clearSession(RoutingContext context, boolean clearAuthFlow) {
        if (clearAuthFlow) {
            // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
            authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                    .doOnError((error) -> LOGGER.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                    .subscribe();
        }
        context.clearUser();
        context.session().destroy();
    }
}
