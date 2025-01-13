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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.dataplane.CredentialService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;

/**
 * The callback route to verify attestations and assertions. Usually this route is <pre>/webauthn/response</pre>
 *
 *  // TODO : This handler exists only because of <a href="https://github.com/gravitee-io/issues/issues/7158">This issue</a>
 *  // should be removed in a future version of AM
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnResponseHandler extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnResponseHandler.class);
    private final WebAuthn webAuthn;
    private final String origin;

    public WebAuthnResponseHandler(UserService userService,
                                   FactorManager factorManager,
                                   Domain domain,
                                   WebAuthn webAuthn,
                                   CredentialService credentialService,
                                   UserAuthenticationManager userAuthenticationManager) {
        setUserService(userService);
        setFactorManager(factorManager);
        setCredentialService(credentialService);
        setUserAuthenticationManager(userAuthenticationManager);
        setDomain(domain);
        this.webAuthn = webAuthn;
        this.origin = getOrigin(domain.getWebAuthnSettings());
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            // might throw runtime exception if there's no json or is bad formed
            final JsonObject webauthnResp = ctx.body().asJsonObject();
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
                    .subscribe(
                            u -> {
                                // create the authentication context
                                final AuthenticationContext authenticationContext = createAuthenticationContext(ctx);
                                // authenticate the user
                                authenticateUser(client, authenticationContext, username, credentialId, h -> {
                                    if (h.failed()) {
                                        logger.error("An error has occurred while authenticating user {}", username, h.cause());
                                        ctx.fail(401);
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
                                        final Credential credential = credentialHandler.result();
                                        if (isEnrollingFido2Factor(ctx)) {
                                            enrollFido2Factor(ctx, authenticatedUser, createEnrolledFactor(session.get(ENROLLED_FACTOR_ID_KEY), credentialId), credential);
                                        } else {
                                            manageFido2FactorEnrollment(ctx, client, credential, authenticatedUser);
                                        }
                                    });
                                });
                            },
                            throwable -> {
                                logger.error("Unexpected exception", throwable);
                                ctx.fail(throwable.getCause());
                            }
                    );
        } catch (IllegalArgumentException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(400);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(e);
        }
    }
}
