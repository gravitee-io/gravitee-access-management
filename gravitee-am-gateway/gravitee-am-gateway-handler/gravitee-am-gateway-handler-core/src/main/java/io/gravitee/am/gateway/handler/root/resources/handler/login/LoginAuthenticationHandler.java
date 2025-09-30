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
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_PROVIDER_ID;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_QUERY_PARAM;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_REMEMBER_ME;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROTOCOL_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROTOCOL_VALUE_SAML_POST;
import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_ME_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RETURN_URL_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Fetch providers information if client using one of them
 * * if social provider found, the context will contain a SOCIAL_PROVIDER_CONTEXT_KEY key
 * * if idp provider found, the context will contain a INTERNAL_PROVIDER_CONTEXT_KEY key
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginAuthenticationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginAuthenticationHandler.class);
    private static final Map<String, String> socialProviders;
    public static final String REMEMBER_ME_ON = "on";

    static {
        Map<String, String> sMap = new HashMap<>();
        sMap.put("github-am-idp", "github");
        sMap.put("google-am-idp", "google");
        sMap.put("twitter-am-idp", "twitter");
        sMap.put("facebook-am-idp", "facebook");
        sMap.put("franceconnect-am-idp", "franceconnect");
        sMap.put("azure-ad-am-idp", "microsoft");
        sMap.put("linkedin-am-idp", "linkedin");
        socialProviders = Collections.unmodifiableMap(sMap);
    }

    public static final String SOCIAL_AUTHORIZE_URL_CONTEXT_KEY = "authorizeUrls";
    private static final String OAUTH2_PROVIDER_CONTEXT_KEY = "oauth2Providers";

    private final IdentityProviderManager identityProviderManager;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LoginAuthenticationHandler(IdentityProviderManager identityProviderManager,
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
        getSocialIdentityProviders(client.getIdentityProviders(), identityProvidersResultHandler -> {
            if (identityProvidersResultHandler.failed()) {
                logger.error("Unable to fetch client social identity providers", identityProvidersResultHandler.cause());
                routingContext.fail(new InvalidRequestException("Unable to fetch client social identity providers"));
            }

            final var providersGroupByExternal = identityProvidersResultHandler.result();

            // no provider, continue
            if (isEmpty(providersGroupByExternal)) {
                routingContext.next();
                return;
            }

            final var internalIdentityProviders = providersGroupByExternal.get(false);
            // internal providers present, add them to the context
            if (!isEmpty(internalIdentityProviders)) {
                routingContext.put(INTERNAL_PROVIDER_CONTEXT_KEY, internalIdentityProviders.stream().map(IdentityProvider::asSafeIdentityProvider).toList());
            }

            final var socialIdentityProviders = providersGroupByExternal.get(true);
            // no social providers, continue
            if (isEmpty(socialIdentityProviders)) {
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
                final var socialProviderData = resultHandler.result();
                if (socialProviderData != null) {
                    final var filteredSocialProviderData = socialProviderData.stream().filter(providerData -> providerData.getIdentityProvider() != null && providerData.getAuthorizeUrl() != null).collect(Collectors.toList());
                    final var providers = filteredSocialProviderData.stream().map(SocialProviderData::getIdentityProvider).map(IdentityProvider::asSafeIdentityProvider).collect(Collectors.toList());
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

    private void getSocialIdentityProviders(SortedSet<ApplicationIdentityProvider> identities, Handler<AsyncResult<Map<Boolean, List<IdentityProvider>>>> resultHandler) {
        if (identities == null) {
            resultHandler.handle(Future.succeededFuture(Map.of()));
        } else {
            resultHandler.handle(Future.succeededFuture(identities.stream()
                    .map(ApplicationIdentityProvider::getIdentity)
                    .map(identityProviderManager::getIdentityProvider)
                    .filter(identityProvider -> identityProvider != null)
                    .collect(Collectors.groupingBy(IdentityProvider::isExternal))));
        }
    }

    private void enhanceSocialIdentityProviders(List<IdentityProvider> identityProviders, RoutingContext context, Handler<AsyncResult<List<SocialProviderData>>> resultHandler) {
        Observable.fromIterable(identityProviders)
                .flatMapSingle(identityProvider -> {
                    IdentityProvider providerCopy = new IdentityProvider(identityProvider);
                    // get social identity provider type (currently use for display purpose (logo, description, ...)
                    providerCopy.setType(socialProviders.getOrDefault(identityProvider.getType(), identityProvider.getType()));
                    // get social sign in url
                    return getAuthorizeUrl(providerCopy.getId(), context)
                            .map(authorizeUrl -> new SocialProviderData(providerCopy, authorizeUrl))
                            .defaultIfEmpty(new SocialProviderData(providerCopy, null));
                })
                .toList()
                .subscribe(socialProviderData -> resultHandler.handle(Future.succeededFuture(socialProviderData)),
                        error -> resultHandler.handle(Future.failedFuture(error)));
    }

    private Maybe<String> getAuthorizeUrl(String identityProviderId, RoutingContext context) {
        return identityProviderManager.get(identityProviderId)
                .flatMap(authenticationProvider -> {
                    // Generate a state containing provider id and current query parameter string. This state will be sent back to AM after social authentication.
                    final JWT stateJwt = prepareState(identityProviderId, context);

                    String redirectUri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login/callback");
                    Maybe<Request> signInURL = ((SocialAuthenticationProvider) authenticationProvider).asyncSignInUrl(redirectUri, stateJwt, jwt -> jwtService.encode(jwt, certificateManager.defaultCertificateProvider()));

                    return signInURL.map(request -> {
                        if (HttpMethod.GET.equals(request.getMethod())) {
                            return request.getUri();
                        } else {
                            // Extract body to convert it to query parameters and use POST form.
                            final Map<String, String> queryParams = getParams(request.getBody());
                            queryParams.put(ACTION_KEY, request.getUri());
                            return UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login/SSO/POST", queryParams);
                        }
                    });
                });
    }

    private static JWT prepareState(String identityProviderId, RoutingContext context) {
        final JWT stateJwt = new JWT();
        stateJwt.setJti(SecureRandomString.generateWithPrefix("_")); // prefix added to conform SAML protocol requirements

        final String protocol = context.session().get(PROTOCOL_KEY);
        if (StringUtils.hasLength(protocol)) {
            // SAML flow, need to keep these session attributes
            // into the state to avoid error when the AM (as SP)
            // will be called back by the external SAML IdP when HTTP-POST
            // binding is in used.
            stateJwt.put(PROTOCOL_KEY, protocol);
            stateJwt.put(RETURN_URL_KEY, context.session().get(RETURN_URL_KEY));
            if (PROTOCOL_VALUE_SAML_POST.equals(protocol)) {
                stateJwt.put(TRANSACTION_ID_KEY, context.session().get(TRANSACTION_ID_KEY));
            }
        }
        stateJwt.put(CLAIM_PROVIDER_ID, identityProviderId);
        stateJwt.put(CLAIM_QUERY_PARAM, context.request().query());
        stateJwt.put(CLAIM_REMEMBER_ME, REMEMBER_ME_ON.equalsIgnoreCase(context.request().formAttributes().get(REMEMBER_ME_PARAM_KEY)));
        return stateJwt;
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
