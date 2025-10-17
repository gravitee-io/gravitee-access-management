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

import io.gravitee.am.common.exception.authentication.AccountEnforcePasswordException;
import io.gravitee.am.common.exception.authentication.AccountStatusException;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_LOGIN_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnAuthenticatorIntegrity.authIntegrity;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class WebAuthnHandler extends AbstractEndpoint implements Handler<RoutingContext> {
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private static final Logger logger = LoggerFactory.getLogger(WebAuthnHandler.class);
    private FactorManager factorManager;
    private UserService userService;
    protected CredentialGatewayService credentialService;
    private UserAuthenticationManager userAuthenticationManager;
    protected DomainDataPlane domainDataPlane;

    protected WebAuthnHandler() {
    }

    protected WebAuthnHandler(TemplateEngine templateEngine) {
        super(templateEngine);
    }

    public void setFactorManager(FactorManager factorManager) {
        this.factorManager = factorManager;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setCredentialService(CredentialGatewayService credentialService) {
        this.credentialService = credentialService;
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }

    public void setDomainDataplane(DomainDataPlane domainDataPlane) {
        this.domainDataPlane = domainDataPlane;
    }

    protected static boolean isEmptyString(JsonObject json, String key) {
        try {
            if (json == null || !json.containsKey(key)) {
                return true;
            }
            String value = json.getString(key);
            return value == null || value.isEmpty();
        } catch (RuntimeException e) {
            return true;
        }
    }

    protected static boolean isEmptyObject(JsonObject json, String key) {
        try {
            if (json == null) {
                return true;
            }
            if (!json.containsKey(key)) {
                return true;
            }
            JsonObject s = json.getJsonObject(key);
            return s == null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    protected void manageFido2FactorEnrollment(RoutingContext ctx, Client client, Credential credential, User authenticatedUser) {
        Optional<Factor> clientFido2Factor = getClientFido2Factor(client);
        if (clientFido2Factor.isPresent()) {
            Optional<EnrolledFactorSecurity> enrolledFido2FactorSecurity = getEnrolledFido2FactorSecurity(authenticatedUser);
            if (enrolledFido2FactorSecurity.isPresent() && enrolledFido2FactorSecurity.get().getValue().equals(credential.getCredentialId())) {
                //user already has fido2 factor for this credential
                updateSessionAuthAndChallengeStatus(ctx);
                updateSessionLoginCompletedStatus(ctx, credential);
                ctx.next();
            } else {
                //save the fido factor
                final EnrolledFactor enrolledFactor = createEnrolledFactor(clientFido2Factor.get().getId(), credential.getCredentialId());
                enrollFido2Factor(ctx, authenticatedUser, enrolledFactor, credential);
            }
        } else {
            updateSessionLoginCompletedStatus(ctx, credential);
            ctx.next();
        }
    }

    protected void enrollFido2Factor(RoutingContext ctx, User authenticatedUser, EnrolledFactor enrolledFactor, Credential credential) {
        userService.upsertFactor(authenticatedUser.getId(), enrolledFactor, new DefaultUser(authenticatedUser))
                .ignoreElement()
                .subscribe(
                        () -> {
                            updateSessionAuthAndChallengeStatus(ctx);
                            updateSessionLoginCompletedStatus(ctx, credential);
                            ctx.next();
                        },
                        error -> {
                            logger.error("Could not update user profile with FIDO2 factor detail", error);
                            ctx.fail(401);
                        }
                );
    }

    protected boolean isEnrollingFido2Factor(RoutingContext ctx) {
        final String factorId = ctx.session().get(ENROLLED_FACTOR_ID_KEY);
        if (factorId == null) {
            return false;
        }

        final Factor factor = factorManager.getFactor(factorId);
        return factor != null && factor.is(FIDO2);
    }

    protected Optional<EnrolledFactorSecurity> getEnrolledFido2FactorSecurity(io.gravitee.am.model.User endUser) {
        if (endUser.getFactors() == null) {
            return Optional.empty();
        }
        return endUser.getFactors()
                .stream()
                .map(EnrolledFactor::getSecurity)
                .filter(Objects::nonNull)
                .filter(enrolledFactorSecurity -> WEBAUTHN_CREDENTIAL.equals(enrolledFactorSecurity.getType()))
                .findFirst();
    }

    private Optional<Factor> getClientFido2Factor(Client client) {
        if (client.getFactorSettings() == null || CollectionUtils.isEmpty(client.getFactorSettings().getApplicationFactors())) {
            return Optional.empty();
        }
        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(f -> factorManager.get(f) != null)
                .map(factorManager::getFactor)
                .filter(f -> f.is(FactorType.FIDO2))
                .findFirst();
    }

    protected void updateSessionAuthAndChallengeStatus(RoutingContext ctx) {
        ctx.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
    }

    protected void updateSessionLoginCompletedStatus(RoutingContext ctx, Credential credential) {
        ctx.session().put(ConstantKeys.PASSWORDLESS_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credential.getCredentialId());
        ctx.session().put(WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY, credential.getId());
        ctx.session().put(USER_LOGIN_COMPLETED_KEY, true);
    }

    protected EnrolledFactor createEnrolledFactor(String factorId, String credentialId) {
        final EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setStatus(ACTIVATED);
        enrolledFactor.setCreatedAt(new Date());
        enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
        enrolledFactor.setSecurity(new EnrolledFactorSecurity(WEBAUTHN_CREDENTIAL, credentialId));

        return enrolledFactor;
    }


    protected AuthenticationContext createAuthenticationContext(RoutingContext context) {
        HttpServerRequest httpServerRequest = context.request();
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(httpServerRequest.getDelegate()));
        if (canSaveIp(context)) {
            authenticationContext.set(Claims.IP_ADDRESS, RequestUtils.remoteAddress(httpServerRequest));
        }
        if (canSaveUserAgent(context)) {
            authenticationContext.set(Claims.USER_AGENT, RequestUtils.userAgent(httpServerRequest));
        }
        authenticationContext.set(Claims.DOMAIN, domainDataPlane.getDomain().getId());
        authenticationContext.setDomain(domainDataPlane.getDomain());
        authenticationContext.setAttribute(DEVICE_ID, context.request().getParam(DEVICE_ID));
        return authenticationContext;
    }

    protected void authenticateUser(Client client,
                                    AuthenticationContext authenticationContext,
                                    String username,
                                    String credentialId,
                                    Handler<AsyncResult<io.vertx.ext.auth.User>> handler) {
        authenticateUser(client, authenticationContext, username, credentialId)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(user)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    protected Single<io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User> authenticateUser(Client client,
                                                                                                      AuthenticationContext authenticationContext,
                                                                                                      String username,
                                                                                                      String credentialId) {
        return credentialService.findByCredentialId(domainDataPlane.getDomain(), credentialId)
                .firstElement()
                .switchIfEmpty(Single.error(() -> new CredentialNotFoundException(credentialId)))
                .flatMap(credential -> userAuthenticationManager.connectWithPasswordless(client, credential.getUserId(), new EndUserAuthentication(username, null, authenticationContext)))
                .map(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User::new)
                .doOnError(error -> {
                    if (error instanceof AccountEnforcePasswordException) {
                        logger.debug("Password required for user '{}': {}", username, error.getMessage());
                    } else if (error instanceof AccountStatusException) {
                        logger.warn("Invalid user status for user '{}': {}", username, error.getMessage());
                    } else {
                        logger.error("An error has occurred while authenticating user: {}", username, error);
                    }
                });
    }

    protected void updateCredential(AuthenticationContext authenticationContext, String credentialId, String userId, Handler<AsyncResult<Credential>> handler) {
        updateCredential(authenticationContext, credentialId, userId, false, handler);
    }

    protected void updateCredential(AuthenticationContext authenticationContext,
                                    String credentialId,
                                    String userId,
                                    boolean afterLogin,
                                    Handler<AsyncResult<Credential>> handler) {
        updateCredential(authenticationContext, credentialId, userId, afterLogin)
                .subscribe(
                        credential -> handler.handle(Future.succeededFuture(credential)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    protected Completable updateCredential(AuthenticationContext authenticationContext, String credentialId, String userId) {
        return updateCredential(authenticationContext, credentialId, userId, false).ignoreElement();
    }

    protected Single<Credential> updateCredential(AuthenticationContext authenticationContext,
                                           String credentialId,
                                           String userId,
                                           boolean afterLogin) {
        return credentialService.findByCredentialId(domainDataPlane.getDomain(), credentialId)
                // filter on userId to restrict the credential to the current user.
                // if credentialToUpdate has null userid, we are in the registration phase
                // we want to assign this credential to the user profile, so we accept it.
                .filter(credentialToUpdate -> credentialToUpdate.getUserId() == null || credentialToUpdate.getUserId().equals(userId))
                .map(credential -> {
                    credential.setUserId(userId);
                    credential.setUserAgent(String.valueOf(authenticationContext.get(Claims.USER_AGENT)));
                    credential.setIpAddress(String.valueOf(authenticationContext.get(Claims.IP_ADDRESS)));
                    return credential;
                })
                .map(credential -> {
                    // update last checked date only after a passwordless login and only if the option is enabled
                    if(afterLogin){
                        return authIntegrity(domainDataPlane.getDomain().getWebAuthnSettings())
                                .updateLastCheckedDate(credential);
                    } else {
                        return credential;
                    }
                })
                .flatMapSingle(credential -> credentialService.update(domainDataPlane.getDomain(), credentialId, credential))
                .firstElement()
                .switchIfEmpty(Single.error(() -> new CredentialNotFoundException(credentialId)))
                .doOnError(error -> logger.error("An error has occurred while updating user {} webauthn credential", userId, error));
    }

}
