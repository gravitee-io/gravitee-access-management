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

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
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

    public LoginCallbackFailureHandler(Domain domain,
                                       AuthenticationFlowContextService authenticationFlowContextService,
                                       IdentityProviderManager identityProviderManager) {
        this.domain = domain;
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.identityProviderManager = identityProviderManager;
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
            // logout user if exists
            if (context.user() != null) {
                // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
                authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                        .doOnError((error) -> logger.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                        .subscribe();

                context.clearUser();
                context.session().destroy();
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
                redirectToSP(originalParams, client, context, throwable);
            } else {
                redirectToLoginPage(originalParams, client, context, throwable);
            }

        } catch (Exception ex) {
            logger.error("An error has occurred while redirecting to the login page", ex);
            context
                    .response()
                    .setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503)
                    .end();
        }
    }

    private void redirectToSP(MultiMap originalParams,
                              Client client,
                              RoutingContext context,
                              Throwable throwable) throws URISyntaxException {
        // Get the SP redirect_uri
        final String spRedirectUri = (originalParams != null && originalParams.get(Parameters.REDIRECT_URI) != null) ?
                originalParams.get(Parameters.REDIRECT_URI) :
                client.getRedirectUris().get(0);

        // append error message
        Map<String, String> query = new LinkedHashMap<>();
        query.put(ConstantKeys.ERROR_PARAM_KEY, "server_error");
        query.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage());
        if (originalParams != null && originalParams.get(Parameters.STATE) != null) {
            query.put(Parameters.STATE, originalParams.get(Parameters.STATE));
        }
        boolean fragment = originalParams != null &&
                originalParams.get(Parameters.RESPONSE_TYPE) != null &&
                (isImplicitFlow(originalParams.get(Parameters.RESPONSE_TYPE)) || isHybridFlow(originalParams.get(Parameters.RESPONSE_TYPE)));

        // prepare final redirect uri
        UriBuilder template = UriBuilder.newInstance();

        // get URI from the redirect_uri parameter
        UriBuilder builder = UriBuilder.fromURIString(spRedirectUri);
        URI redirectUri = builder.build();

        // create final redirect uri
        template.scheme(redirectUri.getScheme())
                .host(redirectUri.getHost())
                .port(redirectUri.getPort())
                .userInfo(redirectUri.getUserInfo())
                .path(redirectUri.getPath());

        // append error parameters in "application/x-www-form-urlencoded" format
        if (fragment) {
            query.forEach((k, v) -> template.addFragmentParameter(k, UriBuilder.encodeURIComponent(v)));
        } else {
            query.forEach((k, v) -> template.addParameter(k, UriBuilder.encodeURIComponent(v)));
        }
        doRedirect(context, template.build().toString());
    }

    private void redirectToLoginPage(MultiMap originalParams,
                                     Client client,
                                     RoutingContext context,
                                     Throwable throwable) {
        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        if (originalParams != null) {
            params.setAll(originalParams);
        }
        params.set(Parameters.CLIENT_ID, client.getClientId());
        params.set(ConstantKeys.ERROR_PARAM_KEY, "social_authentication_failed");
        params.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, UriBuilder.encodeURIComponent(throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage()));
        String uri = getUri(context, params);
        doRedirect(context, uri);
    }

    private String getUri(RoutingContext context, MultiMap params) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        final String replacement = loginSettings != null && loginSettings.isIdentifierFirstEnabled() ? "/identifier" : "";
        final String path = context.request().path().replaceFirst("/callback", replacement);
        return UriBuilderRequest.resolveProxyRequest(context.request(), path, params);
    }
}
