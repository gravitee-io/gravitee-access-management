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

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.SocialAuthHandler;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.PASSWORD_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.USERNAME_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthHandlerImpl implements SocialAuthHandler {

    private final SocialAuthenticationProvider socialAuthenticationProvider;

    public SocialAuthHandlerImpl(SocialAuthenticationProvider authProvider) {
        socialAuthenticationProvider = authProvider;
    }

    /**
     * Override default auth handler handle method to propagate the routing context to the authentication provider
     * @param ctx the routing context
     */
    @Override
    public void handle(RoutingContext ctx) {

        if (handlePreflight(ctx)) {
            return;
        }

        User user = (User) ctx.getDelegate().user();
        if (user != null) {
            // proceed to AuthZ
            ctx.next();
            return;
        }

        JsonObject clientCredentials = new JsonObject()
                .put(USERNAME_PARAM_KEY, "__social__")
                .put(PASSWORD_PARAM_KEY, "__social__")
                .put(Parameters.REDIRECT_URI, UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.request().path()));

        // proceed to authN
        getSocialAuthenticationProvider().authenticate(ctx, clientCredentials, authN -> {
            if (authN.succeeded()) {
                final User authenticated = authN.result();
                ctx.getDelegate().setUser(authenticated);
                ctx.put(ConstantKeys.USER_CONTEXT_KEY, authenticated.getUser());
                ctx.next();
            } else {
                // to allow further processing if needed
                processException(ctx, new HttpException(401, authN.cause()));
            }
        });
    }

    protected void processException(RoutingContext ctx, Throwable exception) {
        if (exception != null && exception.getCause() != null) {
            // override default process exception to redirect to the login page
            if (exception.getCause() instanceof AuthenticationException) {
                ctx.fail(exception.getCause());
                return;
            }
        }
        // fallback 500
        ctx.fail(exception);
    }

    private SocialAuthenticationProvider getSocialAuthenticationProvider() {
        return socialAuthenticationProvider;
    }

    private boolean handlePreflight(RoutingContext ctx) {
        final HttpServerRequest request = ctx.request();
        // See: https://www.w3.org/TR/cors/#cross-origin-request-with-preflight-0
        // Preflight requests should not be subject to security due to the reason UAs will remove the Authorization header
        if (request.method() == HttpMethod.OPTIONS) {
            // check if there is a access control request header
            final String accessControlRequestHeader = ctx.request().getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
            if (accessControlRequestHeader != null) {
                // lookup for the Authorization header
                for (String ctrlReq : accessControlRequestHeader.split(",")) {
                    if (ctrlReq.equalsIgnoreCase("Authorization")) {
                        // this request has auth in access control, so we can allow preflighs without authentication
                        ctx.next();
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
