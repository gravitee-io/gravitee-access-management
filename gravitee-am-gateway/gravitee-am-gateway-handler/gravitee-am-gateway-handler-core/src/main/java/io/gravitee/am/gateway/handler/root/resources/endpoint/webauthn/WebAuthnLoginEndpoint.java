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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Credential;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.util.StringUtils;

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
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private final Domain domain;
    private final WebAuthn webAuthn;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final DeviceService deviceService;
    private final CredentialService credentialService;
    private final String origin;

    public WebAuthnLoginEndpoint(Domain domain,
                                 UserAuthenticationManager userAuthenticationManager,
                                 WebAuthn webAuthn,
                                 ThymeleafTemplateEngine engine,
                                 DeviceIdentifierManager deviceIdentifierManager,
                                 DeviceService deviceService,
                                 CredentialService credentialService) {
        super(engine, userAuthenticationManager);
        this.domain = domain;
        this.webAuthn = webAuthn;
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.deviceService = deviceService;
        this.credentialService = credentialService;
        this.origin = (domain.getWebAuthnSettings() != null
                && domain.getWebAuthnSettings().getOrigin() != null) ?
                domain.getWebAuthnSettings().getOrigin() :
                DEFAULT_ORIGIN;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                authenticate(routingContext);
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

    private void authenticate(RoutingContext ctx) {
        try {
            // support for potential cached javascript files
            // see https://github.com/gravitee-io/issues/issues/7158
            if (MediaType.APPLICATION_JSON.equals(ctx.request().getHeader(HttpHeaders.CONTENT_TYPE))) {
                authenticateV0(ctx);
                return;
            }
            // nominal case
            authenticateV1(ctx);
        } catch (IllegalArgumentException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(400);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(e);
        }
    }

    private void authenticateV0(RoutingContext ctx) {
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

        var rememberDeviceSettings = getRememberDeviceSettings(client);// STEP 18 Generate assertion
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
                }});

            }
        });
    }

    private void authenticateV1(RoutingContext ctx) {
        final String assertion = ctx.request().getParam("assertion");
        if (StringUtils.isEmpty(assertion)) {
            logger.debug("Request missing assertion field");
            ctx.fail(400);
            return;
        }

        final JsonObject webauthnResp = (JsonObject) Json.decodeValue(assertion);
        // input validation
        if (isEmptyString(webauthnResp, "id") ||
                isEmptyString(webauthnResp, "rawId") ||
                isEmptyObject(webauthnResp, "response") ||
                isEmptyString(webauthnResp, "type") ||
                !"public-key".equals(webauthnResp.getString("type"))) {
            logger.debug("Assertion missing one or more of id/rawId/response/type fields, or type is not public-key");
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

        final Client client = ctx.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String userId = session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID);
        final String username = session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
        final String credentialId = webauthnResp.getString("id");

        // authenticate the user
        webAuthn.authenticate(
                // authInfo
                new WebAuthnCredentials()
                        .setOrigin(origin)
                        .setChallenge(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY))
                        .setUsername(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY))
                        .setWebauthn(webauthnResp), authenticate -> {

                    // invalidate the challenge
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID);

                    if (authenticate.succeeded()) {
                        // create the authentication context
                        final AuthenticationContext authenticationContext = createAuthenticationContext(ctx);
                        // authenticate the user
                        authenticateUser(authenticationContext, client, username, h -> {
                            if (h.failed()) {
                                logger.error("An error has occurred while authenticating user {}", username, h.cause());
                                ctx.fail(401);
                                return;
                            }
                            final User user = h.result();
                            final io.gravitee.am.model.User authenticatedUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) user).getUser();
                            // check if the authenticated user is the same as the one in session
                            if (userId == null || !userId.equals(authenticatedUser.getId())) {
                                logger.error("Invalid authenticated user {}, user in session was {}", authenticatedUser.getId(), userId);
                                ctx.fail(401);
                                return;
                            }
                            // update the credential
                            updateCredential(authenticationContext, credentialId, userId, credentialHandler -> {
                                if (credentialHandler.failed()) {
                                    logger.error("An error has occurred while authenticating user {}", username, credentialHandler.cause());
                                    ctx.fail(401);
                                    return;
                                }
                                // save the user into the context
                                ctx.getDelegate().setUser(user);
                                ctx.session().put(ConstantKeys.PASSWORDLESS_AUTH_COMPLETED_KEY, true);
                                ctx.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credentialId);

                                // Now redirect back to authorization endpoint.
                                final MultiMap queryParams = RequestUtils.getCleanedQueryParams(ctx.request());
                                final String returnURL = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);
                                ctx.response()
                                        .putHeader(HttpHeaders.LOCATION, returnURL)
                                        .setStatusCode(302)
                                        .end();
                            });
                        });
                    } else {
                        logger.error("Unexpected exception", authenticate.cause());
                        ctx.fail(authenticate.cause());
                    }
                });
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

    private AuthenticationContext createAuthenticationContext(RoutingContext context) {
        HttpServerRequest httpServerRequest = context.request();
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(httpServerRequest.getDelegate()));
        authenticationContext.set(Claims.ip_address, RequestUtils.remoteAddress(httpServerRequest));
        authenticationContext.set(Claims.user_agent, RequestUtils.userAgent(httpServerRequest));
        authenticationContext.set(Claims.domain, domain.getId());
        return authenticationContext;
    }

    private void authenticateUser(AuthenticationContext authenticationContext, Client client, String username, Handler<AsyncResult<User>> handler) {
        final Authentication authentication = new EndUserAuthentication(username, null, authenticationContext);
        userAuthenticationManager.authenticate(client, authentication, true)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user))),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void updateCredential(AuthenticationContext authenticationContext, String credentialId, String userId, Handler<AsyncResult<Void>> handler) {
        Credential credential = new Credential();
        credential.setUserId(userId);
        credential.setUserAgent(String.valueOf(authenticationContext.get(Claims.user_agent)));
        credential.setIpAddress(String.valueOf(authenticationContext.get(Claims.ip_address)));
        credentialService.update(ReferenceType.DOMAIN, domain.getId(), credentialId, credential)
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }
}
