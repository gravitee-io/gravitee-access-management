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
import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
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

import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;

/**
 * In case of login callback failures, the user will be redirected to the login page with error message
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackFailureHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackFailureHandler.class);
    private final Domain domain;

    private AuthenticationFlowContextService authenticationFlowContextService;

    public LoginCallbackFailureHandler(
            Domain domain,
            AuthenticationFlowContextService authenticationFlowContextService
    ) {
        this.domain = domain;
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception
                    || throwable instanceof AbstractManagementException
                    || throwable instanceof AuthenticationException
                    || throwable instanceof PolicyChainException) {
                redirectToLoginPage(routingContext, throwable);
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

    private void redirectToLoginPage(RoutingContext context, Throwable throwable) {
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
            Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final MultiMap params = MultiMap.caseInsensitiveMultiMap();
            final MultiMap originalParams = context.get(PARAM_CONTEXT_KEY);
            if (originalParams != null) {
                params.setAll(originalParams);
            }
            params.set(Parameters.CLIENT_ID, client.getClientId());
            params.set(ConstantKeys.ERROR_PARAM_KEY, "social_authentication_failed");
            params.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, UriBuilder.encodeURIComponent(throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage()));
            String uri = getUri(context, params);
            doRedirect(context.response(), uri);
        } catch (Exception ex) {
            logger.error("An error has occurred while redirecting to the login page", ex);
            // Note: we can't invoke context.fail cause it'll lead to infinite failure handling.
            context
                    .response()
                    .setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503)
                    .end();
        }
    }

    private String getUri(RoutingContext context, MultiMap params) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        final String replacement = loginSettings.isIdentifierFirstEnabled() ? "/identifier" : "";
        final String path = context.request().path().replaceFirst("/callback", replacement);
        return UriBuilderRequest.resolveProxyRequest(context.request(), path, params);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
