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
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.NotImplementedException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This endpoint returns the WebAuthn credential creation options for the current user.
 *
 * These options object contains a number of required and optional fields that a server specifies to create a new credential for a user.
 *
 * <a href="https://webauthn.guide/#registration">...</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterCredentialsEndpoint extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRegisterCredentialsEndpoint.class);
    private final WebAuthn webAuthn;

    public WebAuthnRegisterCredentialsEndpoint(DomainDataPlane domainDataPlane, WebAuthn webAuthn) {
        setDomainDataplane(domainDataPlane);
        this.webAuthn = webAuthn;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        if (req.method().name().equals("POST")) {
            createCredentialCreationOptions(routingContext);
        } else {
            routingContext.fail(405);
        }
    }

    private void createCredentialCreationOptions(RoutingContext ctx) {
        try {
            // might throw runtime exception if there's no json or is bad formed
            final JsonObject webauthnRegister = ctx.getBodyAsJson();
            final Session session = ctx.session();

            // session validation
            if (session == null) {
                logger.warn("No session or session handler is missing.");
                ctx.fail(500);
                return;
            }

            if (ctx.user() == null) {
                logger.warn("User must be authenticated to register WebAuthn credentials.");
                ctx.fail(401);
                return;
            }

            // input validation
            if (isEmptyString(webauthnRegister, "name") ||
                    isEmptyString(webauthnRegister, "displayName")) {
                ctx.fail(400);
                return;
            }

            // get authenticated user
            User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) ctx.user().getDelegate()).getUser();

            // register credentials
            webAuthn.createCredentialsOptions(webauthnRegister)
                    .subscribe(
                            entries -> {
                                // force user id with our own user id
                                entries.getJsonObject("user").put("id", user.getId());

                                // force registration if option is enabled
                                if (domainDataPlane.getDomain().getWebAuthnSettings() != null && domainDataPlane.getDomain().getWebAuthnSettings().isForceRegistration()) {
                                    entries.remove("excludeCredentials");
                                }

                                // save challenge to the session
                                ctx.session()
                                        .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, entries.getString("challenge"))
                                        .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, webauthnRegister.getString("name"));

                                ctx.response()
                                        .putHeader(io.vertx.core.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                                        .end(Json.encodePrettily(entries));
                            },
                            throwable -> ctx.fail(throwable.getCause())
                    );
        } catch (IllegalArgumentException e) {
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
