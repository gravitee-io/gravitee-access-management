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
package io.gravitee.am.gateway.handler.root.resources.auth.handler.impl;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl.AuthHandlerImpl;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthHandlerImpl extends AuthHandlerImpl {

    private static final String USERNAME_PARAMETER = "username";
    private static final String PASSWORD_PARAMETER = "password";
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String OAUTH2_IDENTIFIER = "_oauth2_";
    private static final String ACCESS_TOKEN_PARAMETER = "access_token";
    private static final String ID_TOKEN_PARAMETER = "id_token";
    private static final String ADDITIONAL_PARAMETERS = "additionalParameters";
    private static final String CLIENT_CONTEXT_KEY = "client";

    public SocialAuthHandlerImpl(SocialAuthenticationProvider authProvider) {
        super(authProvider);
    }

    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        final OAuth2AuthenticationProvider authenticationProvider = context.get(PROVIDER_PARAMETER);
        final Client client = context.get(CLIENT_CONTEXT_KEY);
        final String providerId = context.request().getParam(PROVIDER_PARAMETER);

        try {
            String password = context.request().getParam(authenticationProvider.configuration().getCodeParameter());
            JsonObject clientCredentials = new JsonObject()
                    .put(USERNAME_PARAMETER, OAUTH2_IDENTIFIER)
                    .put(PASSWORD_PARAMETER, password)
                    .put(PROVIDER_PARAMETER, providerId)
                    .put(Parameters.CLIENT_ID, client.getId());

            // set additional parameters
            Map<String, Object> additionalParameters = new HashMap();
            additionalParameters.put(Parameters.REDIRECT_URI, buildRedirectUri(context.request()));
            if (context.get(ACCESS_TOKEN_PARAMETER) != null) {
                additionalParameters.put(ACCESS_TOKEN_PARAMETER, context.get(ACCESS_TOKEN_PARAMETER));
            }
            if (context.get(ID_TOKEN_PARAMETER) != null) {
                additionalParameters.put(ID_TOKEN_PARAMETER, context.get(ID_TOKEN_PARAMETER));
            }
            clientCredentials.put(ADDITIONAL_PARAMETERS, additionalParameters);

            handler.handle(Future.succeededFuture(clientCredentials));
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    @Override
    protected void processException(RoutingContext ctx, Throwable exception) {
        if (exception != null && exception.getCause() != null) {
            // override default process exception to redirect to the login page
            if (exception.getCause() instanceof BadCredentialsException) {
                ctx.fail(exception.getCause());
                return;
            }
        }
        super.processException(ctx, exception);
    }

    private String buildRedirectUri(HttpServerRequest request) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(
                new io.vertx.reactivex.core.http.HttpServerRequest(request),
                request.path(),
                // append provider query param to avoid redirect mismatch exception
                Collections.singletonMap("provider", request.getParam(PROVIDER_PARAMETER)));
    }
}
