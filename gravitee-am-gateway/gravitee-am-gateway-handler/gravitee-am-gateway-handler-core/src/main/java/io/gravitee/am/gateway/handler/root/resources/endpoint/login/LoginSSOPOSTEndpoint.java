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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSSOPOSTEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginSSOPOSTEndpoint.class);
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String FORM_ACTION_CONTEXT_KEY = "action";
    private static final String FORM_PARAMETERS = "parameters";
    private ThymeleafTemplateEngine engine;
    private Domain domain;

    public LoginSSOPOSTEndpoint(ThymeleafTemplateEngine engine, Domain domain) {
        this.engine = engine;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String identityProvider = routingContext.request().getParam(PROVIDER_PARAMETER);
        final AuthenticationProvider authenticationProvider = routingContext.get(PROVIDER_PARAMETER);

        if (!canHandle(authenticationProvider)) {
            logger.error("Identity provider {} invalid or unknown for SSO POST login", identityProvider);
            routingContext.fail(new InvalidRequestException("Identity provider " + identityProvider + " invalid or unknown for SSO POST login"));
            return;
        }

        parseSSOSignInURL(routingContext, identityProvider, (SocialAuthenticationProvider) authenticationProvider, resultHandler -> {
            if (resultHandler.failed()) {
                routingContext.fail(resultHandler.cause());
                return;
            }

            Request request = resultHandler.result();
            // prepare context
            routingContext.put(FORM_ACTION_CONTEXT_KEY, request.getUri());
            routingContext.put(FORM_PARAMETERS, getParams(request.getBody()));

            // render login SSO POST page
            engine.render(routingContext.data(), "login_sso_post", res -> {
                if (res.succeeded()) {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(res.result());
                } else {
                    logger.error("Unable to render Login SSO POST page", res.cause());
                    routingContext.fail(res.cause());
                }
            });
        });
    }

    private void parseSSOSignInURL(RoutingContext routingContext, String identityProvider, SocialAuthenticationProvider authenticationProvider, Handler<AsyncResult<Request>> resultHandler) {
        try {
            Maybe<Request> signInURL = authenticationProvider.asyncSignInUrl(buildRedirectUri(routingContext, identityProvider));
            signInURL
                    .subscribe(
                            request -> {
                                if (HttpMethod.GET.equals(request.getMethod())) {
                                    resultHandler.handle(Future.failedFuture(new InvalidRequestException("SSO Sign In URL HTTP Method must be POST")));
                                } else {
                                    resultHandler.handle(Future.succeededFuture(request));
                                }
                            },
                            error -> resultHandler.handle(Future.failedFuture(new InvalidRequestException("Unable to parse SSO Sign URL"))),
                            () -> resultHandler.handle(Future.failedFuture(new InvalidRequestException("Unable to parse SSO Sign URL"))));
        } catch (Exception ex) {
            logger.error("Failed to parse SSO Sign In URL", ex);
            resultHandler.handle(Future.failedFuture(new InvalidRequestException("Unable to parse SSO Sign URL")));
        }
    }

    private String buildRedirectUri(RoutingContext context, String identityProvider) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login/callback", Collections.singletonMap("provider", identityProvider));
    }

    private boolean canHandle(AuthenticationProvider authenticationProvider) {
        return authenticationProvider != null && (authenticationProvider instanceof SocialAuthenticationProvider);
    }

    private Map<String, String> getParams(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                if (!pair.isEmpty()) {
                    int idx = pair.indexOf("=");
                    query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        }
        return query_pairs;
    }
}
