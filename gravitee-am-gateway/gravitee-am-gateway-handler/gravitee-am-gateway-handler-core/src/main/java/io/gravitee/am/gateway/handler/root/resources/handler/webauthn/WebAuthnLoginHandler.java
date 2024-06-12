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

import io.gravitee.am.common.exception.authentication.AccountDeviceIntegrityException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.AttestationCertificates;
import io.vertx.ext.auth.webauthn.Authenticator;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.util.StringUtils;

import java.time.Instant;
import java.util.Date;

import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_AUTH_ACTION_VALUE_LOGIN;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY;

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
                .subscribe(
                        entries -> {
                            session
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, entries.getString("challenge"))
                                    .put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, username);

                            ctx.put(ConstantKeys.PASSWORDLESS_ASSERTION, entries);
                            ctx.next();
                        },
                        throwable -> {
                            logger.error("Unexpected exception", throwable);
                            ctx.fail(throwable.getCause());
                        }
                );
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
        final AuthenticationContext authenticationContext = createAuthenticationContext(ctx);

        // authenticate the user with its webauthn credential id
        webAuthn.rxAuthenticate(
                        // authInfo
                        new WebAuthnCredentials()
                                .setOrigin(origin)
                                .setChallenge(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY))
                                .setUsername(session.get(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY))
                                .setWebauthn(webauthnResp)).onErrorResumeNext(throwable -> {
                                    if (throwable.getCause() != null) {
                                        logger.error("Unexpected exception", throwable.getCause());
                                        return Single.error(throwable.getCause());
                                    } else {
                                        return Single.error(throwable);
                                    }
                                })
                // make sure user exists in database and all flags are green
                .flatMap(u -> authenticateUser(client, authenticationContext, username, credentialId))
                // check user authenticator conformity
                .flatMap(user -> checkAuthenticatorConformity(credentialId, username).andThen(Single.just(user)))
                // update the credential
                .flatMap(user -> Single.zip(updateCredential(authenticationContext, credentialId, user.getUser().getId(), true), Single.just(user), Tuple::of))
                .doFinally(() -> {
                    // invalidate the challenge
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
                    session.remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                })
                .subscribe(
                        tuple -> {
                            // save the user and credential id into the context
                            final Credential credential = tuple.getT1();
                            final User user = tuple.getT2();
                            ctx.getDelegate().setUser(user);
                            ctx.put(ConstantKeys.USER_CONTEXT_KEY, user.getUser());
                            ctx.put(WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credentialId);
                            ctx.put(WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY, credential.getId());
                            // the user has upgraded from unauthenticated to authenticated
                            // session should be upgraded as recommended by owasp
                            session.regenerateId();
                            // keep the webauthn action into session to be able to do distinction
                            // between login or registration action
                            session.put(PASSWORDLESS_AUTH_ACTION_KEY, PASSWORDLESS_AUTH_ACTION_VALUE_LOGIN);
                            ctx.put(PASSWORDLESS_AUTH_ACTION_KEY, PASSWORDLESS_AUTH_ACTION_VALUE_LOGIN);
                            // manage FIDO2 device enrollment if needed and continue
                            manageFido2FactorEnrollment(ctx, client, credential, user.getUser());
                        },
                        ctx::fail
                );
    }

    protected Completable checkAuthenticatorConformity(String credentialId, String username) {
        final WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        // option is disabled, continue
        if (webAuthnSettings == null || !webAuthnSettings.isEnforceAuthenticatorIntegrity()) {
            return Completable.complete();
        }
        // max Age is not defined, continue
        final Integer maxAge = webAuthnSettings.getEnforceAuthenticatorIntegrityMaxAge();
        if (maxAge == null) {
            logger.warn("WebAuthn enforce authenticator integrity is enabled but max age has not been set");
            return Completable.complete();
        }

        return credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), credentialId)
                .filter(credential -> {
                    final String fmt = credential.getAttestationStatementFormat();
                    final Date lastCheckedAt = credential.getLastCheckedAt();
                    // if the attestation "fmt" set to "none",
                    // then no attestation is provided, and you don’t have anything to verify.
                    if ("none".equals(fmt)) {
                        return false;
                    }
                    // waiting feedback from Vert.x team https://github.com/eclipse-vertx/vertx-auth/issues/619
                    // before removing this condition
                    try {
                        JsonObject jsonObject = new JsonObject(credential.getAttestationStatement());
                        AttestationCertificates attestationCertificates = new AttestationCertificates(jsonObject);
                        // if no certificate chain, skip the verification
                        if (attestationCertificates.getX5c() == null || attestationCertificates.getX5c().isEmpty()) {
                            logger.debug("No certificate chain has been found for credential {}", credentialId);
                            return false;
                        }
                    } catch (Exception ex) {
                        logger.error("Unable to decode credential attestation statement for credential {}", credentialId, ex);
                        return false;
                    }
                    // check only credential with elapsed last checked date
                    return (lastCheckedAt == null || Instant.now().isAfter(lastCheckedAt.toInstant().plusSeconds(maxAge)));
                })
                .firstElement()
                .map(credential -> {
                    Authenticator authenticator = new Authenticator();
                    authenticator.setUserName(credential.getUsername());
                    authenticator.setCredID(credential.getCredentialId());
                    authenticator.setAaguid(credential.getAaguid());
                    authenticator.setCounter(credential.getCounter());
                    authenticator.setPublicKey(credential.getPublicKey());
                    authenticator.setFmt(credential.getAttestationStatementFormat());
                    JsonObject jsonObject = new JsonObject(credential.getAttestationStatement());
                    authenticator.setAttestationCertificates(new AttestationCertificates(jsonObject));
                    // verify integrity (throws RuntimeException if the authenticator is invalid or
                    // returns an MDS statement for this authenticator or null).
                    JsonObject statement = webAuthn.metaDataService().verify(authenticator);
                    return statement != null ? statement : new JsonObject();
                })
                .ignoreElement()
                .onErrorResumeNext(error -> {
                    logger.error("User {} webauthn authenticator {} has not been trusted", username, credentialId, error);
                    return Completable.error(new AccountDeviceIntegrityException("Invalid user webauthn authenticator"));
                });
    }
}
