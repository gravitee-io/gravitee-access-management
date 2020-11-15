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
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Fetch social providers information if client using one of them
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSocialAuthenticationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginSocialAuthenticationHandler.class);
    private static final List<String> socialProviders = Arrays.asList("github", "google", "twitter", "facebook", "bitbucket", "franceconnect");
    private static final String OAUTH2_PROVIDER_CONTEXT_KEY = "oauth2Providers";
    private static final String SOCIAL_PROVIDER_CONTEXT_KEY = "socialProviders";
    private static final String SOCIAL_AUTHORIZE_URL_CONTEXT_KEY = "authorizeUrls";
    private final IdentityProviderManager identityProviderManager;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LoginSocialAuthenticationHandler(IdentityProviderManager identityProviderManager,
                                            JWTService jwtService,
                                            CertificateManager certificateManager) {
        this.identityProviderManager = identityProviderManager;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);

        // fetch client identity providers
        getSocialIdentityProviders(client.getIdentities(), identityProvidersResultHandler -> {
            if (identityProvidersResultHandler.failed()) {
                logger.error("Unable to fetch client social identity providers", identityProvidersResultHandler.cause());
                routingContext.fail(new InvalidRequestException("Unable to fetch client social identity providers"));
            }

            List<IdentityProvider> socialIdentityProviders = identityProvidersResultHandler.result();

            // no social provider, continue
            if (socialIdentityProviders == null || socialIdentityProviders.isEmpty()) {
                routingContext.next();
                return;
            }

            // client enable social connect
            // get social identity providers information to correctly build the login page
            enhanceSocialIdentityProviders(socialIdentityProviders, routingContext, resultHandler -> {
                if (resultHandler.failed()) {
                    logger.error("Unable to enhance client social identity providers", resultHandler.cause());
                    routingContext.fail(new InvalidRequestException("Unable to enhance client social identity providers"));
                }

                // put social providers in context data
                final List<SocialProviderData> socialProviderData = resultHandler.result();
                if (socialProviderData != null) {
                    List<SocialProviderData> filteredSocialProviderData = socialProviderData.stream().filter(providerData -> providerData.getIdentityProvider() != null && providerData.getAuthorizeUrl() != null).collect(Collectors.toList());
                    List<IdentityProvider> providers = filteredSocialProviderData.stream().map(SocialProviderData::getIdentityProvider).collect(Collectors.toList());
                    Map<String, String> authorizeUrls = filteredSocialProviderData.stream().collect(Collectors.toMap(o -> o.getIdentityProvider().getId(), SocialProviderData::getAuthorizeUrl));

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

    private void getSocialIdentityProviders(Set<String> identities, Handler<AsyncResult<List<IdentityProvider>>> resultHandler) {
        if (identities == null) {
            resultHandler.handle(Future.succeededFuture(Collections.emptyList()));
        } else {
            resultHandler.handle(Future.succeededFuture(identities.stream()
                    .map(identityProviderManager::getIdentityProvider)
                    .filter(identityProvider -> identityProvider != null && identityProvider.isExternal())
                    .collect(Collectors.toList())));
        }
    }

    private void enhanceSocialIdentityProviders(List<IdentityProvider> identityProviders, RoutingContext context, Handler<AsyncResult<List<SocialProviderData>>> resultHandler) {
        Observable.fromIterable(identityProviders)
                .flatMapMaybe(identityProvider -> {
                    // get social identity provider type (currently use for display purpose (logo, description, ...)
                    String identityProviderType = identityProvider.getType();
                    Optional<String> identityProviderSocialType = socialProviders.stream().filter(socialProvider -> identityProviderType.toLowerCase().contains(socialProvider)).findFirst();
                    identityProviderSocialType.ifPresent(identityProvider::setType);

                    // get social sign in url
                    return getAuthorizeUrl(identityProvider.getId(), context)
                            .map(authorizeUrl -> new SocialProviderData(identityProvider, authorizeUrl))
                            .defaultIfEmpty(new SocialProviderData(identityProvider, null));
                })
                .toList()
                .subscribe(socialProviderData -> resultHandler.handle(Future.succeededFuture(socialProviderData)),
                        error -> resultHandler.handle(Future.failedFuture(error)));
    }

    private Maybe<String> getAuthorizeUrl(String identityProviderId, RoutingContext context) {
        return identityProviderManager.get(identityProviderId)
                .flatMap(authenticationProvider -> {
                    // Generate a state containing provider id and current query parameter string. This state will be sent back to AM after social authentication.
                    final JWT stateJwt = new JWT();
                    stateJwt.put("p", identityProviderId);
                    stateJwt.put("q", context.request().query());

                    return jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider())
                            .flatMapMaybe(state -> {
                                String redirectUri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login/callback");
                                Maybe<Request> signInURL = ((SocialAuthenticationProvider) authenticationProvider).asyncSignInUrl(redirectUri, state);

                                return signInURL.map(request -> {
                                    if(HttpMethod.GET.equals(request.getMethod())) {
                                        return request.getUri();
                                    }else {
                                        // Extract body to convert it to query parameters and use POST form.
                                        final Map<String, String> queryParams = getParams(request.getBody());
                                        queryParams.put(ACTION_KEY, request.getUri());
                                        return UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login/SSO/POST", queryParams);
                                    }
                                });
                            });
                });
    }

    private static class SocialProviderData {
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

    private Map<String, String> getParams(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                if (!pair.isEmpty()) {
                    int idx = pair.indexOf("=");
                    query_pairs.put(pair.substring(0, idx), UriBuilder.encodeURIComponent(pair.substring(idx + 1)));
                }
            }
        }
        return query_pairs;
    }
}
