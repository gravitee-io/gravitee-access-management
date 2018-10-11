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
package io.gravitee.am.gateway.handler.vertx.handler.login.endpoint;

import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.utils.UriBuilder;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginEndpointHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginEndpointHandler.class);
    private final static List<String> socialProviders = Arrays.asList("github", "google", "twitter", "facebook", "bitbucket");
    private static final String DOMAIN_CONTEXT_KEY = "domain";
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String PARAM_CONTEXT_KEY = "param";
    private static final String ERROR_PARAM_KEY = "error";
    private static final String OAUTH2_PROVIDER_CONTEXT_KEY = "oauth2Providers";
    private static final String OAUTH2_AUTHORIZE_URL_CONTEXT_KEY = "authorizeUrls";
    private ThymeleafTemplateEngine engine;
    private Domain domain;
    private IdentityProviderManager identityProviderManager;

    public LoginEndpointHandler() {}

    public LoginEndpointHandler(ThymeleafTemplateEngine thymeleafTemplateEngine,
                                Domain domain,
                                IdentityProviderManager identityProviderManager) {
        this.engine = thymeleafTemplateEngine;
        this.domain = domain;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final Set<String> oauth2Identities = client.getOauth2Identities();

        // no OAuth2/Social provider render login page
        if (oauth2Identities == null || oauth2Identities.isEmpty()) {
            renderLoginPage(routingContext);
            return;
        }

        // client enable OAuth 2.0/social connect
        // get OAuth 2.0 client identity providers information to correctly build the login page
        getOAuth2Identities(oauth2Identities, routingContext.request(), resultHandler -> {
            if (resultHandler.failed()) {
                logger.error("Unable to fetch client OAuth 2.0 identity providers", resultHandler.cause());
                routingContext.fail(new InvalidRequestException("Unable to fetch client OAuth 2.0 identity providers"));
                return;
            }

            // put oauth2 providers in context data
            final List<OAuth2ProviderData> oAuth2ProviderData = resultHandler.result();
            routingContext.put(OAUTH2_PROVIDER_CONTEXT_KEY, oAuth2ProviderData.stream().map(OAuth2ProviderData::getIdentityProvider).collect(Collectors.toList()));
            routingContext.put(OAUTH2_AUTHORIZE_URL_CONTEXT_KEY, oAuth2ProviderData.stream().collect(Collectors.toMap(o -> o.getIdentityProvider().getId(), o -> o.getAuthorizeUrl())));

            // render login page
            renderLoginPage(routingContext);
        });
    }

    private void renderLoginPage(RoutingContext routingContext) {
        // remove client context to avoid any leaks from custom login pages
        routingContext.remove(CLIENT_CONTEXT_KEY);
        // put domain in context data
        routingContext.put(DOMAIN_CONTEXT_KEY, domain);

        // put additional parameter (backward compatibility)
        final String error = routingContext.request().getParam(ERROR_PARAM_KEY);
        Map<String, String> params = new HashMap<>();
        if (error != null) {
            params.put(ERROR_PARAM_KEY, error);
        }
        params.put(OAuth2Constants.CLIENT_ID, routingContext.request().getParam(OAuth2Constants.CLIENT_ID));
        routingContext.put(PARAM_CONTEXT_KEY, params);

        // render the login page
        engine.render(routingContext, "login", res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error("Unable to render login page", res.cause());
                routingContext.fail(res.cause());
            }
        });
    }

    private void getOAuth2Identities(Set<String> oauth2Identities, HttpServerRequest request, Handler<AsyncResult<List<OAuth2ProviderData>>> resultHandler) {
        Observable.fromIterable(oauth2Identities)
                .flatMapSingle(oauth2Identity -> getIdentityProvider(oauth2Identity)
                        .zipWith(getAuthorizeUrl(oauth2Identity, request),
                                (identityProvider, authorizeUrl) -> new OAuth2ProviderData(identityProvider, authorizeUrl)))
                .toList()
                .subscribe(identityProviders -> resultHandler.handle(Future.succeededFuture(identityProviders)),
                        error -> resultHandler.handle(Future.failedFuture(error)));
    }

    private Single<IdentityProvider> getIdentityProvider(String identityProviderId) {
        return identityProviderManager.getIdentityProvider(identityProviderId)
                .map(identityProvider -> {
                    String identityProviderType = identityProvider.getType();
                    Optional<String> identityProviderSocialType = socialProviders.stream().filter(socialProvider -> identityProviderType.toLowerCase().contains(socialProvider)).findFirst();
                    if (identityProviderSocialType.isPresent()) {
                        identityProvider.setType(identityProviderSocialType.get());
                    }
                    return identityProvider;
                }).toSingle();
    }

    private Single<String> getAuthorizeUrl(String identityProviderId, HttpServerRequest request) {
        return identityProviderManager.get(identityProviderId)
                .map(authenticationProvider -> {
                    OAuth2AuthenticationProvider oAuth2AuthenticationProvider = (OAuth2AuthenticationProvider) authenticationProvider;
                    OAuth2IdentityProviderConfiguration configuration = oAuth2AuthenticationProvider.configuration();
                    UriBuilder builder = UriBuilder.fromHttpUrl(configuration.getUserAuthorizationUri());
                    builder.addParameter(OAuth2Constants.CLIENT_ID, configuration.getClientId());
                    builder.addParameter(OAuth2Constants.REDIRECT_URI, buildRedirectUri(request, identityProviderId));
                    builder.addParameter(OAuth2Constants.RESPONSE_TYPE, OAuth2Constants.CODE);
                    if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
                        builder.addParameter(OAuth2Constants.SCOPE, String.join(" ", configuration.getScopes()));
                    }
                    return builder.build().toString();
                }).toSingle();
    }

    private String buildRedirectUri(HttpServerRequest request, String identity) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(
                request,
                "/" + domain.getPath() + "/login/callback",
                Collections.singletonMap("provider", identity), true, true);
    }

    private class OAuth2ProviderData {
        private IdentityProvider identityProvider;
        private String authorizeUrl;

        public OAuth2ProviderData(IdentityProvider identityProvider, String authorizeUrl) {
            this.identityProvider = identityProvider;
            this.authorizeUrl = authorizeUrl;
        }

        public IdentityProvider getIdentityProvider() {
            return identityProvider;
        }

        public void setIdentityProvider(IdentityProvider identityProvider) {
            this.identityProvider = identityProvider;
        }

        public String getAuthorizeUrl() {
            return authorizeUrl;
        }

        public void setAuthorizeUrl(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl;
        }
    }
}
