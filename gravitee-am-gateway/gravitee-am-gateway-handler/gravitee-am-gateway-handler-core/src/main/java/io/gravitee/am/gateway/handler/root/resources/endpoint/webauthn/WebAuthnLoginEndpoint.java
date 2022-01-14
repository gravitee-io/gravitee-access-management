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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnLoginEndpoint extends WebAuthnEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnLoginEndpoint.class);

    private final Domain domain;
    private final WebAuthn webAuthn;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final DeviceService deviceService;

    public WebAuthnLoginEndpoint(Domain domain,
                                 UserAuthenticationManager userAuthenticationManager,
                                 WebAuthn webAuthn,
                                 ThymeleafTemplateEngine engine,
                                 DeviceIdentifierManager deviceIdentifierManager,
                                 DeviceService deviceService
    ) {
        super(engine, userAuthenticationManager, null);
        this.domain = domain;
        this.webAuthn = webAuthn;
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.deviceService = deviceService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                getCredentials(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            // prepare the context
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));

            final String loginActionKey = routingContext.get(CONTEXT_PATH) + "/login";
            routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), loginActionKey, queryParams, true));
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, Collections.singletonMap(Parameters.CLIENT_ID, client.getClientId()));

            // render the webauthn login page
            final Map<String, Object> data = generateData(routingContext, domain, client);
            data.putAll(deviceIdentifierManager.getTemplateVariables(client));
            renderPage(routingContext, data, client, logger, "Unable to render WebAuthn login page");
        } catch (Exception ex) {
            logger.error("An error occurs while rendering WebAuthn login page", ex);
            routingContext.fail(503);
        }
    }

    /**
     * The callback route to create login attestations. Usually this route is <pre>/webauthn/login</pre>
     */
    private void getCredentials(RoutingContext ctx) {
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

            var rememberDeviceSettings = getRememberDeviceSettings(client);
            // STEP 18 Generate assertion
            webAuthn.getCredentialsOptions(username, generateServerGetAssertion -> {
                if (generateServerGetAssertion.failed()) {
                    logger.error("Unexpected exception", generateServerGetAssertion.cause());
                    ctx.fail(generateServerGetAssertion.cause());
                } else {
                    final JsonObject getAssertion = generateServerGetAssertion.result();
                    // check if user exists in AM
                    checkUser(client, username, new VertxHttpServerRequest(ctx.request().getDelegate()), h -> {
                        // if user doesn't exists to need to set values in the session
                        if (h.result() != null) {
                            session
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, getAssertion.getString("challenge"))
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, username)
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID, h.result().getId());
                            if (rememberDeviceSettings.isActive()) {
                                var deviceId = webauthnLogin.getString(DEVICE_ID);
                                var deviceType = webauthnLogin.getString(DEVICE_TYPE);
                                checkIfDeviceExists(ctx, client, h.result().getId(), deviceId, deviceType, rememberDeviceSettings)
                                        .doFinally(() -> buildResponse(ctx, getAssertion))
                                        .subscribe();
                            } else {
                                buildResponse(ctx, getAssertion);
                            }
                        } else {
                            buildResponse(ctx, getAssertion);
                        }
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

    private void buildResponse(RoutingContext ctx, JsonObject getAssertion) {
        ctx.response()
                .putHeader(io.vertx.core.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .end(Json.encodePrettily(getAssertion));
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private Completable checkIfDeviceExists(RoutingContext routingContext,
                                            Client client,
                                            String userId,
                                            String deviceId,
                                            String deviceType,
                                            RememberDeviceSettings rememberDeviceSettings) {
        var domain = client.getDomain();
        var deviceIdentifierId = rememberDeviceSettings.getDeviceIdentifierId();
        if (isNullOrEmpty(deviceId)) {
            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            return Completable.complete();
        } else {
            return this.deviceService.deviceExists(domain, client.getClientId(), userId, deviceIdentifierId, deviceId).flatMapMaybe(isEmpty -> {
                if (!isEmpty) {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
                } else {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
                    routingContext.session().put(DEVICE_ID, deviceId);
                    routingContext.session().put(DEVICE_TYPE, deviceType);
                }
                return Maybe.just(isEmpty);
            }).ignoreElement();
        }
    }

    @Override
    public String getTemplateSuffix() {
        return Template.WEBAUTHN_LOGIN.template();
    }
}
