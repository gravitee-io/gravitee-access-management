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
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.utils.UriBuilder;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
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
    private ThymeleafTemplateEngine engine;
    private Domain domain;
    private ClientService clientService;
    private IdentityProviderManager identityProviderManager;

    public LoginEndpointHandler() {}

    public LoginEndpointHandler(ThymeleafTemplateEngine thymeleafTemplateEngine,
                                Domain domain,
                                ClientService clientService,
                                IdentityProviderManager identityProviderManager) {
        this.engine = thymeleafTemplateEngine;
        this.domain = domain;
        this.clientService = clientService;
        this.identityProviderManager = identityProviderManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String clientId = routingContext.request().getParam(OAuth2Constants.CLIENT_ID);

        if (clientId == null || clientId.isEmpty()) {
            logger.error(OAuth2Constants.CLIENT_ID + " parameter is required");
            routingContext.fail(400);
            return;
        }

        clientService
                .findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(clientId)))
                .flatMapObservable(client -> {
                    if (client.getOauth2Identities() == null) {
                        return Observable.fromIterable(Collections.emptyList());
                    }
                    return Observable.fromIterable(client.getOauth2Identities());
                })
                .flatMapSingle(oAuth2Identity -> getIdentityProvider(oAuth2Identity).zipWith(getAuthorizeUrl(oAuth2Identity, routingContext.request()),
                        ((identityProvider, authorizeUrl) -> new OAuth2ProviderData(identityProvider, authorizeUrl))))
                .toList()
                .subscribe(oAuth2ProvidersData -> {
                    // set context data
                    routingContext.put("domain", domain);
                    routingContext.put("oauth2Providers", oAuth2ProvidersData.stream().map(oAuth2ProviderData -> oAuth2ProviderData.getIdentityProvider()).collect(Collectors.toList()));
                    routingContext.put("authorizeUrls", oAuth2ProvidersData.stream().collect(Collectors.toMap(o -> o.getIdentityProvider().getId(), o -> o.getAuthorizeUrl())));

                    // backward compatibility
                    Map<String, String> params = new HashMap<>();
                    String error = routingContext.request().getParam("error");
                    if (error != null) {
                        params.put("error", error);
                    }
                    params.put(OAuth2Constants.CLIENT_ID, routingContext.request().getParam(OAuth2Constants.CLIENT_ID));
                    routingContext.put("param", params);

                    // render the login page
                    engine.render(routingContext, "login", res -> {
                        if (res.succeeded()) {
                            routingContext.response().end(res.result());
                        } else {
                            routingContext.fail(res.cause());
                        }
                    });

                }, error -> {
                    if (error instanceof AbstractManagementException) {
                        AbstractManagementException managementException = (AbstractManagementException) error;
                        routingContext.fail(managementException.getHttpStatusCode());
                    } else {
                        routingContext.fail(error);
                    }
                });
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
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
                Collections.singletonMap("provider", identity), true);
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
