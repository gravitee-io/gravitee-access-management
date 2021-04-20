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
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl.AuthHandlerImpl;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthHandlerImpl extends AuthHandlerImpl {

    private static final String USERNAME_PARAMETER = "username";
    private static final String PASSWORD_PARAMETER = "password";
    private static final String PROVIDER_PARAMETER = "provider";
    private SocialAuthenticationProvider socialAuthenticationProvider;

    public SocialAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
        socialAuthenticationProvider = (SocialAuthenticationProvider) authProvider;
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

        User user = ctx.user();
        if (user != null) {
            // proceed to AuthZ
            authorizeUser(ctx, user);
            return;
        }
        // parse the request in order to extract the credentials object
        parseCredentials(
            ctx,
            res -> {
                if (res.failed()) {
                    processException(ctx, res.cause());
                    return;
                }
                // check if the user has been set
                User updatedUser = ctx.user();

                if (updatedUser != null) {
                    Session session = ctx.session();
                    if (session != null) {
                        // the user has upgraded from unauthenticated to authenticated
                        // session should be upgraded as recommended by owasp
                        session.regenerateId();
                    }
                    // proceed to AuthZ
                    authorizeUser(ctx, updatedUser);
                    return;
                }

                // proceed to authN
                getSocialAuthenticationProvider()
                    .authenticate(
                        ctx,
                        res.result(),
                        authN -> {
                            if (authN.succeeded()) {
                                User authenticated = authN.result();
                                ctx.setUser(authenticated);
                                Session session = ctx.session();
                                if (session != null) {
                                    // the user has upgraded from unauthenticated to authenticated
                                    // session should be upgraded as recommended by owasp
                                    session.regenerateId();
                                }
                                // proceed to AuthZ
                                authorizeUser(ctx, authenticated);
                            } else {
                                String header = authenticateHeader(ctx);
                                if (header != null) {
                                    ctx.response().putHeader("WWW-Authenticate", header);
                                }
                                // to allow further processing if needed
                                processException(ctx, new HttpStatusException(401, authN.cause()));
                            }
                        }
                    );
            }
        );
    }

    protected final void parseAuthorization(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        try {
            JsonObject clientCredentials = new JsonObject()
                .put(USERNAME_PARAMETER, "__social__")
                .put(PASSWORD_PARAMETER, "__social__")
                .put(Parameters.REDIRECT_URI, buildRedirectUri(context.request()));

            handler.handle(Future.succeededFuture(clientCredentials));
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    @Override
    protected void processException(RoutingContext ctx, Throwable exception) {
        if (exception != null && exception.getCause() != null) {
            // override default process exception to redirect to the login page
            if (exception.getCause() instanceof AuthenticationException) {
                ctx.fail(exception.getCause());
                return;
            }
        }
        super.processException(ctx, exception);
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

    private void authorizeUser(RoutingContext ctx, User user) {
        authorize(
            user,
            authZ -> {
                if (authZ.failed()) {
                    processException(ctx, authZ.cause());
                    return;
                }
                // success, allowed to continue
                ctx.next();
            }
        );
    }

    private String buildRedirectUri(io.vertx.core.http.HttpServerRequest request) throws URISyntaxException {
        return UriBuilderRequest.resolveProxyRequest(
            new io.vertx.reactivex.core.http.HttpServerRequest(request),
            request.path(),
            // append provider query param to avoid redirect mismatch exception
            Collections.singletonMap("provider", request.getParam(PROVIDER_PARAMETER))
        );
    }
}
