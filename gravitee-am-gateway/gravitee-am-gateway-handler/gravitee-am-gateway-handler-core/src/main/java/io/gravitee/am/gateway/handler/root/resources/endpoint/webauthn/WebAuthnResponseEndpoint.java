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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthn;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callback route to verify attestations and assertions. Usually this route is <pre>/webauthn/response</pre>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnResponseEndpoint extends WebAuthnEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(WebAuthnResponseEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String PASSWORDLESS_AUTH_COMPLETED  = "passwordlessAuthCompleted";
    private WebAuthn webAuthn;

    public WebAuthnResponseEndpoint(UserAuthenticationManager userAuthenticationManager,
                                    WebAuthn webAuthn) {
        super(userAuthenticationManager);
        this.webAuthn = webAuthn;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            // might throw runtime exception if there's no json or is bad formed
            final JsonObject webauthnResp = ctx.getBodyAsJson();
            // input validation
            if (isEmptyString(webauthnResp, "id") ||
                    isEmptyString(webauthnResp, "rawId") ||
                    isEmptyObject(webauthnResp, "response") ||
                    isEmptyString(webauthnResp, "type") ||
                    !"public-key".equals(webauthnResp.getString("type"))) {
                logger.debug("Response missing one or more of id/rawId/response/type fields, or type is not public-key");
                ctx.fail(400);
                return;
            }

            // session validation
            final Session session = ctx.session();
            if (ctx.session() == null) {
                logger.error("No session or session handler is missing.");
                ctx.fail(500);
                return;
            }

            final Client client = ctx.get(CLIENT_CONTEXT_KEY);
            final String userId = session.get("userId");
            final String username = session.get("username");
            webauthnResp.put("userId", userId);

            // authenticate the user
            webAuthn.authenticate(
                    // authInfo
                    new WebAuthnCredentials()
                            .setChallenge(session.get("challenge"))
                            .setUsername(session.get("username"))
                            .setWebauthn(webauthnResp), authenticate -> {

                        // invalidate the challenge
                        session.remove("challenge");

                        if (authenticate.succeeded()) {
                            authenticateUser(ctx, client, username, h -> {
                                if (h.failed()) {
                                    logger.error("An error has occurred while authenticating user {}", username, authenticate.cause());
                                    ctx.fail(401);
                                    return;
                                }
                                final User user = h.result();
                                // save the user into the context
                                ctx.getDelegate().setUser(user);
                                // the user has upgraded from unauthenticated to authenticated
                                // session should be upgraded as recommended by owasp
                                session.regenerateId();
                                ctx.session().put(PASSWORDLESS_AUTH_COMPLETED, true);
                                // Now redirect back to the original url
                                String returnURL = session.get(FormLoginHandler.DEFAULT_RETURN_URL_PARAM);
                                ctx.response().putHeader(HttpHeaders.LOCATION, returnURL).end();
                            });
                        } else {
                            logger.error("Unexpected exception", authenticate.cause());
                            ctx.fail(authenticate.cause());
                        }
                    });
        } catch (IllegalArgumentException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(400);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(e);
        }
    }

    private void authenticateUser(RoutingContext context, Client client, String username, Handler<AsyncResult<User>> handler) {
        HttpServerRequest httpServerRequest = context.request();
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(httpServerRequest.getDelegate()));
        final Authentication authentication = new EndUserAuthentication(username, null, authenticationContext);
        authenticationContext.set(Claims.ip_address, RequestUtils.remoteAddress(httpServerRequest));
        authenticationContext.set(Claims.user_agent, RequestUtils.userAgent(httpServerRequest));
        authenticationContext.set(Claims.domain, client.getDomain());

        userAuthenticationManager.authenticate(client, authentication, true)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user))),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }
}
