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

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.*;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class WebAuthnHandler extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnHandler.class);
    private FactorManager factorManager;
    private FactorService factorService;
    private CredentialService credentialService;
    private UserAuthenticationManager userAuthenticationManager;
    protected Domain domain;

    public WebAuthnHandler() {
    }

    public WebAuthnHandler(TemplateEngine templateEngine) {
        super(templateEngine);
    }

    public void setFactorManager(FactorManager factorManager) {
        this.factorManager = factorManager;
    }

    public void setFactorService(FactorService factorService) {
        this.factorService = factorService;
    }

    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    protected static boolean isEmptyString(JsonObject json, String key) {
        try {
            if (json == null) {
                return true;
            }
            if (!json.containsKey(key)) {
                return true;
            }
            String s = json.getString(key);
            return s == null || "".equals(s);
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

    protected void manageFido2FactorEnrollment(RoutingContext ctx, Client client, String credentialId, User authenticatedUser) {
        Optional<Factor> clientFido2Factor = getClientFido2Factor(client);
        if (clientFido2Factor.isPresent()) {
            Optional<EnrolledFactorSecurity> enrolledFido2FactorSecurity = getEnrolledFido2FactorSecurity(authenticatedUser);
            if (hasAnyFactorOtherThanFido2Factor(authenticatedUser).isPresent()) {
                updateSessionLoginCompletedStatus(ctx, credentialId);
                ctx.next();
            } else if (enrolledFido2FactorSecurity.isPresent() && enrolledFido2FactorSecurity.get().getValue().equals(credentialId)) {
                //user already has fido2 factor for this credential
                updateSessionAuthAndChallengeStatus(ctx);
                updateSessionLoginCompletedStatus(ctx, credentialId);
                ctx.next();
            } else {
                //save the fido factor
                final EnrolledFactor enrolledFactor = createEnrolledFactor(clientFido2Factor.get().getId(), credentialId);
                enrollFido2Factor(ctx, authenticatedUser, enrolledFactor);
            }
        } else {
            updateSessionLoginCompletedStatus(ctx, credentialId);
            ctx.next();
        }
    }

    protected void enrollFido2Factor(RoutingContext ctx, User authenticatedUser, EnrolledFactor enrolledFactor) {
        final var credentialId = enrolledFactor.getSecurity().getValue();
        factorService.enrollFactor(authenticatedUser, enrolledFactor)
                .ignoreElement()
                .subscribe(
                        () -> {
                            updateSessionAuthAndChallengeStatus(ctx);
                            updateSessionLoginCompletedStatus(ctx, credentialId);
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
        Set<String> factors = client.getFactors();
        if (factors == null || factors.isEmpty()) {
            return Optional.empty();
        }
        return client.getFactors().stream()
                .filter(f -> factorManager.get(f) != null)
                .map(factorManager::getFactor)
                .filter(f -> f.is(FactorType.FIDO2))
                .findFirst();
    }

    private Optional<FactorType> hasAnyFactorOtherThanFido2Factor(io.gravitee.am.model.User endUser) {
        if (endUser.getFactors() == null) {
            return Optional.empty();
        }
        return endUser.getFactors()
                .stream()
                .map(it -> factorManager.getFactor(it.getFactorId()))
                .map(Factor::getFactorType)
                .filter(factoryType -> !FactorType.RECOVERY_CODE.equals(factoryType))
                .filter(factoryType -> !FactorType.FIDO2.equals(factoryType))
                .findAny();
    }

    protected void updateSessionAuthAndChallengeStatus(RoutingContext ctx) {
        ctx.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
    }

    protected void updateSessionLoginCompletedStatus(RoutingContext ctx, String credentialId) {
        ctx.session().put(ConstantKeys.PASSWORDLESS_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credentialId);
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
            authenticationContext.set(Claims.ip_address, RequestUtils.remoteAddress(httpServerRequest));
        }
        if (canSaveUserAgent(context)) {
            authenticationContext.set(Claims.user_agent, RequestUtils.userAgent(httpServerRequest));
        }
        authenticationContext.set(Claims.domain, domain.getId());
        authenticationContext.setAttribute(DEVICE_ID, context.request().getParam(DEVICE_ID));
        return authenticationContext;
    }

    protected void authenticateUser(Client client,
                                    AuthenticationContext authenticationContext,
                                    String username,
                                    String credentialId,
                                    Handler<AsyncResult<io.vertx.ext.auth.User>> handler) {
        credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), credentialId)
                .firstElement()
                .switchIfEmpty(Maybe.error(new CredentialNotFoundException(credentialId)))
                .flatMapSingle(credential -> userAuthenticationManager.connectPreAuthenticatedUser(client, credential.getUserId(), new EndUserAuthentication(username, null, authenticationContext)))
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user))),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    protected void updateCredential(AuthenticationContext authenticationContext, String credentialId, String userId, Handler<AsyncResult<Void>> handler) {
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
