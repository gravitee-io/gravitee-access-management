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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.context.provider.UserProperties;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthn;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterEndpoint extends WebAuthnEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRegisterEndpoint.class);
    private static final String SKIP_WEBAUTHN_PARAM_KEY = "skipWebAuthN";

    private final Domain domain;
    private final WebAuthn webAuthn;
    private final ThymeleafTemplateEngine engine;

    public WebAuthnRegisterEndpoint(Domain domain,
                                    UserAuthenticationManager userAuthenticationManager,
                                    WebAuthn webAuthn,
                                    ThymeleafTemplateEngine engine) {
        super(userAuthenticationManager);
        this.domain = domain;
        this.webAuthn = webAuthn;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                createCredentials(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            // session validation
            if (routingContext.session() == null) {
                logger.warn("No session or session handler is missing.");
                routingContext.fail(500);
                return;
            }

            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to register WebAuthn credentials.");
                routingContext.fail(401);
                return;
            }

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());

            // check if user has skipped this step
            final HttpServerRequest request = routingContext.request();
            if (Boolean.parseBoolean(request.getParam(SKIP_WEBAUTHN_PARAM_KEY))) {
                queryParams.remove(SKIP_WEBAUTHN_PARAM_KEY);
                String returnURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);
                routingContext.session().put(ConstantKeys.WEBAUTHN_SKIPPED_KEY, true);
                // Now redirect back to the original url
                doRedirect(routingContext.response(), returnURL);
                return;
            }

            // prepare the context
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final UserProperties userProperties = new UserProperties(user);

            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            final String skipAction = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams.set("skipWebAuthN", "true"), true);

            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.SKIP_ACTION_KEY, skipAction);
            routingContext.put(ConstantKeys.DOMAIN_CONTEXT_KEY, domain);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, userProperties);
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, Collections.singletonMap(Parameters.CLIENT_ID, client.getClientId()));

            if(domain.getWebAuthnSettings() != null && domain.getWebAuthnSettings().getAuthenticatorAttachment() != null) {
                routingContext.put(ConstantKeys.PARAM_AUTHENTICATOR_ATTACHMENT_KEY, domain.getWebAuthnSettings().getAuthenticatorAttachment().getValue());
            }

            // render the webauthn register page
            engine.render(routingContext.data(), getTemplateFileName(client), res -> {
                if (res.succeeded()) {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(res.result());
                } else {
                    logger.error("Unable to render WebAuthn register page", res.cause());
                    routingContext.fail(res.cause());
                }
            });
        } catch (Exception ex) {
            logger.error("An error has occurred while rendering WebAuthn register page", ex);
            routingContext.fail(503);
        }
    }

    /**
     * The callback route to create registration attestations. Usually this route is <pre>/webauthn/register</pre>
     */
    private void createCredentials(RoutingContext ctx) {
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

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(ctx.request());
            final String returnURL = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);
            final Boolean skipEnrollment = webauthnRegister.getBoolean("skip_user_webauthn_registration", false);
            if (skipEnrollment) {
                ctx.session().put(ConstantKeys.WEBAUTHN_SKIPPED_KEY, true);
                // Now redirect back to the original url
                ctx.response()
                        .putHeader(HttpHeaders.LOCATION, returnURL)
                        .end();
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
            webAuthn.createCredentialsOptions(webauthnRegister, createCredentialsOptions -> {
                if (createCredentialsOptions.failed()) {
                    ctx.fail(createCredentialsOptions.cause());
                    return;
                }

                final JsonObject credentialsOptions = createCredentialsOptions.result();
                // force user id with our own user id
                credentialsOptions.getJsonObject("user").put("id", user.getId());

                // force registration if option is enabled
                if (domain.getWebAuthnSettings() != null && domain.getWebAuthnSettings().isForceRegistration()) {
                    credentialsOptions.remove("excludeCredentials");
                }

                // save challenge to the session
                ctx.session()
                        .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, credentialsOptions.getString("challenge"))
                        .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, webauthnRegister.getString("name"))
                        .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID, user.getId());

                ctx.response()
                        .putHeader(io.vertx.core.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .end(Json.encodePrettily(credentialsOptions));
            });
        } catch (IllegalArgumentException e) {
            ctx.fail(400);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(e);
        }
    }

    private String getTemplateFileName(Client client) {
        return Template.WEBAUTHN_REGISTER.template() + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
