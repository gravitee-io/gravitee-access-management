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
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetch social providers information if client using one of them
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSocialAuthenticationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginSocialAuthenticationHandler.class);
    private static final List<String> socialProviders = Arrays.asList("github", "google", "twitter", "facebook", "bitbucket");
    private static final String OAUTH2_PROVIDER_CONTEXT_KEY = "oauth2Providers";
    private static final String SOCIAL_PROVIDER_CONTEXT_KEY = "socialProviders";
    private static final String SOCIAL_AUTHORIZE_URL_CONTEXT_KEY = "authorizeUrls";
    private static final String CLIENT_CONTEXT_KEY = "client";
    private IdentityProviderManager identityProviderManager;
    private Domain domain;

    public LoginSocialAuthenticationHandler(IdentityProviderManager identityProviderManager, Domain domain) {
        this.identityProviderManager = identityProviderManager;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);

        // fetch client identity providers
        getIdentityProviders(client.getIdentities(), identityProvidersResultHandler -> {
            if (identityProvidersResultHandler.failed()) {
                logger.error("Unable to fetch client identity providers", identityProvidersResultHandler.cause());
                routingContext.fail(new InvalidRequestException("Unable to fetch client identity providers"));
            }

            List<IdentityProvider> identityProviders = identityProvidersResultHandler.result();
            List<IdentityProvider> socialIdentityProviders = identityProviders.stream().filter(IdentityProvider::isExternal).collect(Collectors.toList());

            // no social provider, continue
            if (socialIdentityProviders == null || socialIdentityProviders.isEmpty()) {
                routingContext.next();
                return;
            }

            // client enable social connect
            // get social identity providers information to correctly build the login page
            enhanceSocialIdentityProviders(socialIdentityProviders, routingContext.request(), resultHandler -> {
                if (resultHandler.failed()) {
                    logger.error("Unable to enhance client social identity providers", resultHandler.cause());
                    routingContext.fail(new InvalidRequestException("Unable to enhance client social identity providers"));
                }

                // put social providers in context data
                final List<SocialProviderData> socialProviderData = resultHandler.result();
                if (socialProviderData != null) {
                    List<SocialProviderData> filteredSocialProviderData = socialProviderData.stream().filter(providerData -> providerData.getIdentityProvider() != null && providerData.getAuthorizeUrl() != null).collect(Collectors.toList());
                    List<IdentityProvider> providers = filteredSocialProviderData.stream().map(SocialProviderData::getIdentityProvider).collect(Collectors.toList());
                    Map<String, String> authorizeUrls = filteredSocialProviderData.stream().collect(Collectors.toMap(o -> o.getIdentityProvider().getId(), o -> o.getAuthorizeUrl()));

                    // backwards compatibility
                    routingContext.put(OAUTH2_PROVIDER_CONTEXT_KEY, providers);
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, providers);
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, authorizeUrls);
                }

                // continue
                routingContext.next();
            });
        });

    }

    private void getIdentityProviders(Set<String> identities, Handler<AsyncResult<List<IdentityProvider>>> resultHandler) {
        if (identities == null) {
            resultHandler.handle(Future.succeededFuture(Collections.emptyList()));
        } else {
            Observable.fromIterable(identities)
                    .flatMapMaybe(identity -> identityProviderManager.getIdentityProvider(identity))
                    .toList()
                    .subscribe(
                            identityProviders -> resultHandler.handle(Future.succeededFuture(identityProviders)),
                            error -> resultHandler.handle(Future.failedFuture(error)));
        }
    }

    private void enhanceSocialIdentityProviders(List<IdentityProvider> identityProviders, HttpServerRequest request, Handler<AsyncResult<List<SocialProviderData>>> resultHandler) {
        Observable.fromIterable(identityProviders)
                .flatMapMaybe(identityProvider -> {
                    // get social identity provider type (currently use for display purpose (logo, description, ...)
                    String identityProviderType = identityProvider.getType();
                    Optional<String> identityProviderSocialType = socialProviders.stream().filter(socialProvider -> identityProviderType.toLowerCase().contains(socialProvider)).findFirst();
                    if (identityProviderSocialType.isPresent()) {
                        identityProvider.setType(identityProviderSocialType.get());
                    }
                    // get social sign in url
                    return getAuthorizeUrl(identityProvider.getId(), request)
                            .map(authorizeUrl -> new SocialProviderData(identityProvider, authorizeUrl))
                            .defaultIfEmpty(new SocialProviderData(identityProvider,null));
                })
                .toList()
                .subscribe(socialProviderData -> resultHandler.handle(Future.succeededFuture(socialProviderData)),
                        error -> resultHandler.handle(Future.failedFuture(error)));
    }

    private Maybe<String> getAuthorizeUrl(String identityProviderId, HttpServerRequest request) {
        return identityProviderManager.get(identityProviderId)
                .flatMap(authenticationProvider -> {
                    Map<String, String> parameters = Collections.singletonMap("provider", identityProviderId);
                    String redirectUri = buildUri(request, "/" + domain.getPath() + "/login/callback", parameters);
                    Request signInURL = ((SocialAuthenticationProvider) authenticationProvider).signInUrl(redirectUri);
                    if (signInURL == null) {
                        return Maybe.empty();
                    }
                    return Maybe.just(HttpMethod.GET.equals(signInURL.getMethod()) ? signInURL.getUri() : buildUri(request, "/" + domain.getPath() + "/login/SSO/POST", parameters));
                });
    }

    private String buildUri(HttpServerRequest request, String path, Map<String, String> parameters) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(request, path, parameters);
    }

    private class SocialProviderData {
        private IdentityProvider identityProvider;
        private String authorizeUrl;

        public SocialProviderData(IdentityProvider identityProvider, String authorizeUrl) {
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
