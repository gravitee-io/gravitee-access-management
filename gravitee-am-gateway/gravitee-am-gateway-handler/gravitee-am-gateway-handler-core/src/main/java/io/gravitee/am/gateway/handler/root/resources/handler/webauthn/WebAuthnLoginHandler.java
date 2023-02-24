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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.util.StringUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnLoginHandler extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnLoginHandler.class);
    private final WebAuthn webAuthn;
    private final String origin;

    public WebAuthnLoginHandler(FactorService factorService,
                                FactorManager factorManager,
                                Domain domain,
                                WebAuthn webAuthn,
                                CredentialService credentialService,
                                UserAuthenticationManager userAuthenticationManager) {
        setFactorService(factorService);
        setFactorManager(factorManager);
        setCredentialService(credentialService);
        setUserAuthenticationManager(userAuthenticationManager);
        setDomain(domain);
        this.webAuthn = webAuthn;
        this.origin = getOrigin(domain.getWebAuthnSettings());
    }

    @Override
    public void handle(RoutingContext routingContext) {
        authenticate(routingContext);
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

        final String username = webauthnLogin.getString("name");

        // STEP 18 Generate assertion
        webAuthn.getCredentialsOptions(username)
                .doOnSuccess(entries -> {
                    session
                            .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, entries.getString("challenge"))
                            .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, username);

                    ctx.put(ConstantKeys.PASSWORDLESS_ASSERTION, entries);
                    ctx.next();
                })
                .doOnError(throwable -> {
                    logger.error("Unexpected exception", throwable);
                    ctx.fail(throwable.getCause());
                }).subscribe();
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
        final String username = session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
        final String credentialId = webauthnResp.getString("id");

        // authenticate the user
        webAuthn.rxAuthenticate(
                        // authInfo
                        new WebAuthnCredentials()
                                .setOrigin(origin)
                                .setChallenge(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY))
                                .setUsername(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY))
                                .setWebauthn(webauthnResp))
                .doFinally(() -> {
                    // invalidate the challenge
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                })
                .subscribe(u -> {
                    // create the authentication context
                    final AuthenticationContext authenticationContext = createAuthenticationContext(ctx);
                    // authenticate the user
                    authenticateUser(client, authenticationContext, username, credentialId, h -> {
                        if (h.failed()) {
                            if (h.cause() instanceof AuthenticationException) {
                                ctx.fail(h.cause());
                            } else {
                                logger.error("An error has occurred while authenticating user {}", username, h.cause());
                                ctx.fail(401);
                            }
                            return;
                        }
                        final User user = h.result();
                        // save the user into the context
                        ctx.getDelegate().setUser(user);
                        ctx.put(ConstantKeys.USER_CONTEXT_KEY, ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) user).getUser());
                        // the user has upgraded from unauthenticated to authenticated
                        // session should be upgraded as recommended by owasp
                        session.regenerateId();
                        final io.gravitee.am.model.User authenticatedUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) user).getUser();
                        // update the credential
                        updateCredential(authenticationContext, credentialId, authenticatedUser.getId(), credentialHandler -> {
                            if (credentialHandler.failed()) {
                                logger.error("An error has occurred while authenticating user {}", username, credentialHandler.cause());
                                ctx.fail(401);
                                return;
                            }
                            manageFido2FactorEnrollment(ctx, client, credentialId, authenticatedUser);
                        });
                    });
                }, throwable -> {
                    logger.error("Unexpected exception", throwable);
                    ctx.fail(throwable.getCause());
                });
    }
}
