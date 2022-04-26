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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.NotImplementedException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This endpoint returns the WebAuthn credential request options for the current user.
 *
 * These options tell the browser which credentials the server would like the user to authenticate with.
 * The credentialId retrieved and saved during registration is passed in here.
 * The server can optionally indicate what transports it prefers, like USB, NFC, and Bluetooth.
 *
 * https://webauthn.guide/#authentication
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnLoginCredentialsEndpoint extends WebAuthnEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnLoginCredentialsEndpoint.class);
    private final WebAuthn webAuthn;

    public WebAuthnLoginCredentialsEndpoint(UserAuthenticationManager userAuthenticationManager,
                                            WebAuthn webAuthn, FactorManager factorManager, FactorService factorService) {
        super(userAuthenticationManager, factorService, factorManager);
        this.webAuthn = webAuthn;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "POST":
                createCredentialRequestOptions(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void createCredentialRequestOptions(RoutingContext ctx) {
        try {
            // might throw runtime exception if there's no json or is bad formed
            final JsonObject webauthnLogin = ctx.getBodyAsJson();
            final Session session = ctx.session();

            // input validation
            if (isEmptyString(webauthnLogin, "name")) {
                logger.debug("Request missing username field");
                ctx.fail(400);
                return;
            }

            // session validation
            if (session == null) {
                logger.warn("No session or session handler is missing.");
                ctx.fail(500);
                return;
            }

            final Client client = ctx.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final String username = webauthnLogin.getString("name");

            // STEP 18 Generate assertion
            webAuthn.getCredentialsOptions(username, generateServerGetAssertion -> {
                if (generateServerGetAssertion.failed()) {
                    logger.error("Unexpected exception", generateServerGetAssertion.cause());
                    ctx.fail(generateServerGetAssertion.cause());
                } else {
                    final JsonObject getAssertion = generateServerGetAssertion.result();
                    // check if user exists in AM
                    checkUser(client, username, new VertxHttpServerRequest(ctx.request().getDelegate()), h -> {
                        // if user doesn't exist to need to set values in the session
                        if (h.result() != null) {
                            session
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, getAssertion.getString("challenge"))
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, username)
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID, h.result().getId());
                        }
                        ctx.response()
                                .putHeader(io.vertx.core.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                                .end(Json.encodePrettily(getAssertion));
                    });

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

    @Override
    public String getTemplateSuffix() {
        // this endpoint returns json response, no HTML template required
        throw new NotImplementedException("No need to render a template");
    }

}
