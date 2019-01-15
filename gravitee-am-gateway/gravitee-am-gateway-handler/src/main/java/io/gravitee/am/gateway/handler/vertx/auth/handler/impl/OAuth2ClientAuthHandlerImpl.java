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
package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.service.utils.UriBuilder;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.service.exception.authentication.InternalAuthenticationServiceException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ClientAuthHandlerImpl extends AuthHandlerImpl {

    private final static Logger logger = LoggerFactory.getLogger(OAuth2ClientAuthHandlerImpl.class);
    private final static String USERNAME_PARAMETER = "username";
    private final static String PASSWORD_PARAMETER = "password";
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String OAUTH2_IDENTIFIER = "_oauth2_";
    private IdentityProviderManager identityProviderManager;

    public OAuth2ClientAuthHandlerImpl(AuthProvider authProvider, IdentityProviderManager identityProviderManager) {
        super(authProvider);
        this.identityProviderManager = identityProviderManager;
    }

    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        try {
            final String providerId = context.request().getParam(PROVIDER_PARAMETER);
            final String clientId = getQueryParams(context.session().get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM)).get(OAuth2Constants.CLIENT_ID);

            if (providerId != null) {
                identityProviderManager.get(providerId)
                        .map(authenticationProvider -> {
                            if (!(authenticationProvider instanceof OAuth2AuthenticationProvider)) {
                                throw new InternalAuthenticationServiceException("OAuth2 Provider " + providerId + "is not social");
                            }
                            return (OAuth2AuthenticationProvider) authenticationProvider;
                        })
                        .subscribe(authenticationProvider -> {
                            try {
                                String password = context.request().getParam(authenticationProvider.configuration().getCodeParameter());
                                JsonObject clientCredentials = new JsonObject()
                                        .put(USERNAME_PARAMETER, OAUTH2_IDENTIFIER)
                                        .put(PASSWORD_PARAMETER, password)
                                        .put(PROVIDER_PARAMETER, providerId)
                                        .put(OAuth2Constants.CLIENT_ID, clientId)
                                        .put(OAuth2Constants.REDIRECT_URI, buildRedirectUri(context.request()));
                                handler.handle(Future.succeededFuture(clientCredentials));
                            } catch (Exception e) {
                                handler.handle(Future.failedFuture(e));
                            }
                        }, error -> handler.handle(Future.failedFuture(error)));
            } else {
                handler.handle(Future.failedFuture(UNAUTHORIZED));
            }
        } catch (Exception e) {
            logger.error("Failed to parseAuthorization for OAuth 2.0 provider", e);
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }

    private String buildRedirectUri(HttpServerRequest request) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(
                new io.vertx.reactivex.core.http.HttpServerRequest(request),
                request.path(),
                // append provider query param to avoid redirect mismatch exception
                Collections.singletonMap("provider", request.getParam(PROVIDER_PARAMETER)));
    }

    private Map<String, String> getQueryParams(String url) throws URISyntaxException {
        URI uri = UriBuilder.fromHttpUrl(url).build();
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }
}
