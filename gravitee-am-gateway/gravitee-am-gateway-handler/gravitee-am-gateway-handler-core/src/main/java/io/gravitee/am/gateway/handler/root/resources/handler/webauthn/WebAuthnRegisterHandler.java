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
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.util.StringUtils;

import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterHandler extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRegisterHandler.class);
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private final WebAuthn webAuthn;
    private final String origin;

    public WebAuthnRegisterHandler(FactorService factorService,
                                   FactorManager factorManager,
                                   Domain domain,
                                   WebAuthn webAuthn,
                                   CredentialService credentialService) {
        setFactorService(factorService);
        setFactorManager(factorManager);
        setCredentialService(credentialService);
        setDomain(domain);
        this.webAuthn = webAuthn;
        this.origin = (domain.getWebAuthnSettings() != null
                && domain.getWebAuthnSettings().getOrigin() != null) ?
                domain.getWebAuthnSettings().getOrigin() :
                DEFAULT_ORIGIN;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        register(routingContext);
    }

    private void register(RoutingContext ctx) {
        try {
            // support for potential cached javascript files
            // see https://github.com/gravitee-io/issues/issues/7158
            if (MediaType.APPLICATION_JSON.equals(ctx.request().getHeader(HttpHeaders.CONTENT_TYPE))) {
                registerV0(ctx);
                return;
            }
            // nominal case
            registerV1(ctx);
        } catch (IllegalArgumentException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(400);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception", e);
            ctx.fail(e);
        }
    }

    private void registerV0(RoutingContext ctx) {
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
            logger.debug("Request missing name or displayName field");
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
                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, webauthnRegister.getString("name"));

            ctx.put(ConstantKeys.PASSWORDLESS_ASSERTION, credentialsOptions);
            ctx.next();
        });
    }

    private void registerV1(RoutingContext ctx) {
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

        if (ctx.user() == null) {
            logger.warn("User must be authenticated to register WebAuthn credentials.");
            ctx.fail(401);
            return;
        }

        final User authenticatedUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) ctx.user().getDelegate()).getUser();
        final Client client = ctx.get(ConstantKeys.CLIENT_CONTEXT_KEY);
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

                    if (authenticate.succeeded()) {
                        // create the authentication context
                        final AuthenticationContext authenticationContext = createAuthenticationContext(ctx);
                        // update the credential
                        updateCredential(authenticationContext, credentialId, authenticatedUser.getId(), credentialHandler -> {
                            if (credentialHandler.failed()) {
                                logger.error("An error has occurred while updating credential for user {}", username, credentialHandler.cause());
                                ctx.fail(401);
                                return;
                            }
                            if (isEnrollingFido2Factor(ctx)) {
                                enrollFido2Factor(ctx, authenticatedUser, createEnrolledFactor(session.get(ENROLLED_FACTOR_ID_KEY), credentialId));
                            } else {
                                manageFido2FactorEnrollment(ctx, client, credentialId, authenticatedUser);
                            }
                            ctx.next();
                        });
                    } else {
                        logger.error("Unexpected exception", authenticate.cause());
                        ctx.fail(authenticate.cause());
                    }
                });
    }
}
