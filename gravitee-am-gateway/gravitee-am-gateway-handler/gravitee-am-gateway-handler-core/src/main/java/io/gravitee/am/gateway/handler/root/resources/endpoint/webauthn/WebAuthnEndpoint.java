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

import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.Request;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
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
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_LOGIN_COMPLETED_KEY;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class WebAuthnEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final UserAuthenticationManager userAuthenticationManager;

    protected final FactorManager factorManager;

    protected final FactorService factorService;

    WebAuthnEndpoint(TemplateEngine templateEngine, UserAuthenticationManager userAuthenticationManager, FactorService factorService, FactorManager factorManager) {
        super(templateEngine);
        this.userAuthenticationManager = userAuthenticationManager;
        this.factorManager = factorManager;
        this.factorService = factorService;
    }

    WebAuthnEndpoint(UserAuthenticationManager userAuthenticationManager, FactorService factorService, FactorManager factorManager) {
        super(null);
        this.userAuthenticationManager = userAuthenticationManager;
        this.factorManager = factorManager;
        this.factorService = factorService;
    }

    /**
     * Check if a given user name exists
     * @param client OAuth 2.0 client
     * @param username User name
     * @param handler Response handler
     */
    protected void checkUser(Client client, String username, Request request, Handler<AsyncResult<User>> handler) {
        userAuthenticationManager.loadUserByUsername(client, username, request)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(user)),
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.failedFuture(new UsernameNotFoundException(username)))
                );
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

    protected void manageFido2FactorEnrollmentThenRedirect(RoutingContext ctx, Client client, String credentialId, io.vertx.ext.auth.User user, User authenticatedUser) {
        Optional<Factor> clientFido2Factor = getClientFido2Factor(client);
        if (clientFido2Factor.isPresent()) {
            Optional<EnrolledFactorSecurity> enrolledFido2FactorSecurity = getEnrolledFido2FactorSecurity(authenticatedUser);
            if (hasAnyFactorOtherThanFido2Factor(authenticatedUser).isPresent()) {
                updateSessionLoginCompletedStatus(ctx, user, credentialId);
                redirectToAuthorize(ctx);
            } else if (enrolledFido2FactorSecurity.isPresent() && enrolledFido2FactorSecurity.get().getValue().equals(credentialId)) {
                //user already has fido2 factor for this credential
                updateSessionAuthAndChallengeStatus(ctx);
                updateSessionLoginCompletedStatus(ctx, user, credentialId);
                redirectToAuthorize(ctx);
            } else {
                //save the fido factor
                final EnrolledFactor enrolledFactor = createEnrolledFactor(clientFido2Factor.get().getId(), credentialId);
                enrollFido2Factor(ctx, user, authenticatedUser, enrolledFactor);
            }
        } else {
            updateSessionLoginCompletedStatus(ctx, user, credentialId);
            redirectToAuthorize(ctx);
        }
    }

    protected void enrollFido2Factor(RoutingContext ctx, io.vertx.ext.auth.User user, User authenticatedUser, EnrolledFactor enrolledFactor) {
        final var credentialId = enrolledFactor.getSecurity().getValue();
        factorService.enrollFactor(authenticatedUser, enrolledFactor)
                .ignoreElement()
                .subscribe(
                        () -> {
                            updateSessionAuthAndChallengeStatus(ctx);
                            updateSessionLoginCompletedStatus(ctx, user, credentialId);
                            redirectToAuthorize(ctx);
                        },
                        error -> {
                            logger.error("Could not update user profile with FIDO2 factor detail", error);
                            ctx.fail(401);
                        }
                );
    }

    protected boolean isEnrollingFido2Factor(RoutingContext ctx) {
        final String factorId = ctx.session().get(ENROLLED_FACTOR_ID_KEY);
        if(factorId == null) {
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

    protected void updateSessionLoginCompletedStatus(RoutingContext ctx, io.vertx.ext.auth.User user, String credentialId){
        // save the user and login completed status into the context
        ctx.getDelegate().setUser(user);
        ctx.session().put(ConstantKeys.PASSWORDLESS_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credentialId);
        ctx.session().put(USER_LOGIN_COMPLETED_KEY, true);
    }

    protected void redirectToAuthorize(RoutingContext ctx){
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(ctx.request());
        final String returnURL = getReturnUrl(ctx, queryParams);
        ctx.response().putHeader(HttpHeaders.LOCATION, returnURL).setStatusCode(302).end();
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

}
