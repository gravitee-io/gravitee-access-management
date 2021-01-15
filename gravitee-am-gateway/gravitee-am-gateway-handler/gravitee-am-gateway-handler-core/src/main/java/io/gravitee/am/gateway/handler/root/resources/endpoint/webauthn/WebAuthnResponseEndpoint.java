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
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthn;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnCredentials;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * The callback route to verify attestations and assertions. Usually this route is <pre>/webauthn/response</pre>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnResponseEndpoint extends WebAuthnEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnResponseEndpoint.class);
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private WebAuthn webAuthn;
    private CredentialService credentialService;
    private Domain domain;
    private String origin;

    public WebAuthnResponseEndpoint(UserAuthenticationManager userAuthenticationManager,
                                    WebAuthn webAuthn,
                                    CredentialService credentialService,
                                    Domain domain) {
        super(userAuthenticationManager);
        this.webAuthn = webAuthn;
        this.credentialService = credentialService;
        this.domain = domain;
        this.origin = (domain.getWebAuthnSettings() != null
                && domain.getWebAuthnSettings().getOrigin() != null) ?
                domain.getWebAuthnSettings().getOrigin() :
                DEFAULT_ORIGIN;
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
                                    final String returnURL = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH) + "/oauth/authorize", queryParams);
                                    ctx.response().putHeader(HttpHeaders.LOCATION, returnURL).end();
                                });
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
