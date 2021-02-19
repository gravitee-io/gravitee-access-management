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

import io.gravitee.am.common.exception.authentication.NegotiateContinueException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.common.http.HttpStatusCode.UNAUTHORIZED_401;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginNegotiateAuthenticationHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginNegotiateAuthenticationHandler.class);

    private final UserAuthProvider authProvider;
    private final ThymeleafTemplateEngine engine;

    public LoginNegotiateAuthenticationHandler(UserAuthProvider authProvider, ThymeleafTemplateEngine engine) {
        this.authProvider = authProvider;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext context) {
        String authHeader = context.request().getHeader(io.vertx.core.http.HttpHeaders.AUTHORIZATION);
        boolean hasNegotiateToken = (authHeader != null && authHeader.trim().startsWith(AUTH_NEGOTIATE_KEY));
        if (hasNegotiateToken) {
            MultiMap params = context.request().params();
            String clientId = params.get(Parameters.CLIENT_ID);
            if (clientId == null) {
                LOGGER.warn("No client id - did you forget to include client_id query parameter ?");
                context.fail(400);
                return;
            }

            JsonObject authInfo = new JsonObject()
                    .put(USERNAME_PARAM_KEY, "spnego token")
                    .put(PASSWORD_PARAM_KEY, authHeader.replaceFirst(AUTH_NEGOTIATE_KEY, "").trim())
                    .put(Claims.ip_address, RequestUtils.remoteAddress(context.request()))
                    .put(Claims.user_agent, RequestUtils.userAgent(context.request()))
                    .put(Parameters.CLIENT_ID, clientId);

            authProvider.authenticate(context, authInfo, res -> {
                if (res.failed()) {
                    LOGGER.debug("SPNEGO token is invalid, continue flow to display login form");
                    if (res.cause() instanceof NegotiateContinueException) {
                        // mutual authentication is requested by client,
                        // update the context with the challenge token
                        // redirect to login page
                        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request())
                                .add(ASK_FOR_NEGOTIATE_KEY, "true")
                                .add(NEGOTIATE_CONTINUE_TOKEN_KEY, ((NegotiateContinueException) res.cause()).getToken());
                        final String redirectUri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login", queryParams);
                        context.response().putHeader(HttpHeaders.LOCATION, redirectUri)
                                .setStatusCode(302)
                                .end();
                        return;
                    }

                    context.next();
                    return;
                }

                // authentication success
                // set user into the context and continue
                final User result = res.result();
                context.getDelegate().setUser(result);
                context.put(ConstantKeys.USER_CONTEXT_KEY, result.getUser());
                context.next();
            });
        } else {
            LOGGER.debug("SPNEGO token is missing, continue flow to display login form");

            // create post action url.
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request()).add(ASK_FOR_NEGOTIATE_KEY, "true");
            context.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login", queryParams));

            // Render login SSO SPNEGO form.
            // Responds with an 401 response with the "WWW-Authenticate: Negotiate" HTTP Response Header.
            // This tells the web browser that it needs to check with the local OS regarding what options it has available
            // from the Negotiate Security Support Provider (SSP) to authenticate the user.
            engine.render(context.data(), "login_sso_spnego", res -> {
                if (res.succeeded()) {
                    context.response().putHeader(HttpHeaders.WWW_AUTHENTICATE, AUTH_NEGOTIATE_KEY);
                    context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    context.response().setStatusCode(UNAUTHORIZED_401);
                    context.response().end(res.result());
                } else {
                    LOGGER.error("Unable to render Login SSO SPNEGO page", res.cause());
                    context.fail(res.cause());
                }
            });
        }
    }
}
