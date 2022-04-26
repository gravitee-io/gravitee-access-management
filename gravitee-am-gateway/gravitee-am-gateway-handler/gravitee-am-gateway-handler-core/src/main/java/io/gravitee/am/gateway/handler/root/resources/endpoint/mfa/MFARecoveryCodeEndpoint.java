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

package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.api.RecoveryFactor;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.factor.api.FactorContext.KEY_USER;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFARecoveryCodeEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFARecoveryCodeEndpoint.class);

    private final Domain domain;
    private final UserService userService;
    private final FactorManager factorManager;
    private final ApplicationContext applicationContext;

    public MFARecoveryCodeEndpoint(TemplateEngine templateEngine, Domain domain, UserService userService,
                                   FactorManager factorManager, ApplicationContext applicationContext) {
        super(templateEngine);
        this.domain = domain;
        this.userService = userService;
        this.factorManager = factorManager;
        this.applicationContext = applicationContext;
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_RECOVERY_CODE.template();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                update(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void update(RoutingContext routingContext) {
        if (failIfUserIsNotPresent(routingContext)) {
            return;
        }

        try {
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Optional<EnrolledFactor> recoveryFactor = getRecoveryFactor(endUser);

            if (recoveryFactor.isPresent()) {
                final EnrolledFactor factorToUpdate = recoveryFactor.get();
                factorToUpdate.setStatus(FactorStatus.ACTIVATED);

                userService.updateFactor(endUser.getId(), factorToUpdate, new DefaultUser(endUser))
                        .ignoreElement()
                        .subscribe(
                                () -> {
                                    doRedirect(routingContext);
                                },
                                error -> {
                                    logger.error("Failed to generate recovery code. Continue with flow as verification is successful", error);
                                    doRedirect(routingContext);
                                }
                        );
            }
        } catch (Exception ex) {
            logger.error("An error occurs while updating recovery code factor status", ex);
            routingContext.fail(503);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        if (failIfUserIsNotPresent(routingContext)) {
            return;
        }

        try {
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

            //recovery code
            final Optional<EnrolledFactorSecurity> existingEnrolledFactorSecurity = getEnrolledRecoveryCodeFactorSecurity(endUser);
            if (existingEnrolledFactorSecurity.isPresent()) {
                final List<String> recoveryCodes = (List<String>) existingEnrolledFactorSecurity.get().getAdditionalData().get(RECOVERY_CODE);
                renderRecoveryCodePage(routingContext, client, recoveryCodes);
            } else {
                generateRecoveryCode(endUser, client).subscribe(
                        enrolledFactorSecurity -> {
                            final List<String> recoveryCodes = (List<String>) enrolledFactorSecurity.getAdditionalData().get(RECOVERY_CODE);
                            renderRecoveryCodePage(routingContext, client, recoveryCodes);
                        },
                        error -> {
                            logger.error("Failed to generate recovery code. Continue with flow as verification is successful", error);
                            doRedirect(routingContext);
                        }
                );
            }

        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA recovery code page", ex);
            routingContext.fail(503);
        }
    }

    private Optional<EnrolledFactorSecurity> getEnrolledRecoveryCodeFactorSecurity(User endUser) {
        return endUser.getFactors()
                .stream()
                .filter(ftr -> ftr.getSecurity() != null && ftr.getSecurity().getType().equals(RECOVERY_CODE))
                .map(EnrolledFactor::getSecurity)
                .findFirst();
    }

    private void doRedirect(RoutingContext routingContext) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String returnUrl = getReturnUrl(routingContext, queryParams);
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, returnUrl)
                .setStatusCode(302)
                .end();
    }

    private Optional<EnrolledFactor> getRecoveryFactor(io.gravitee.am.model.User user) {
        if (user.getFactors() == null) {
            return Optional.empty();
        }

        return user.getFactors()
                .stream()
                .filter(ftr -> ftr.getSecurity() != null && ftr.getSecurity().getType().equals(RECOVERY_CODE))
                .findFirst();
    }

    private boolean failIfUserIsNotPresent(RoutingContext routingContext){
        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to view recovery code.");
            routingContext.fail(401);
            return true;
        }

        return false;
    }

    private Single<EnrolledFactorSecurity> generateRecoveryCode(io.gravitee.am.model.User endUser, Client client) throws TechnicalException {
        final Factor recoveryFactor = getClientRecoveryFactor(client).get();
        final FactorProvider recoveryFactorProvider = factorManager.get(recoveryFactor.getId());
        final Map<String, Object> factorData = Map.of(
                FactorContext.KEY_RECOVERY_FACTOR,
                recoveryFactor, KEY_USER, endUser);
        final FactorContext recoveryFactorCtx = new FactorContext(applicationContext, factorData);

        return ((RecoveryFactor) recoveryFactorProvider).generateRecoveryCode(recoveryFactorCtx);
    }

    private Optional<Factor> getClientRecoveryFactor(Client client) throws TechnicalException {
        return Optional.ofNullable(client.getFactors().stream()
                .filter(f -> factorManager.get(f) != null)
                .map(factorManager::getFactor)
                .filter(f -> f.is(FactorType.RECOVERY_CODE))
                .findFirst().orElseThrow(() ->
                    new TechnicalException("Client does not have recovery code factor which should not happen.")
                ));
    }

    private void renderRecoveryCodePage(RoutingContext routingContext, Client client, List<String> codes){
        //add recoveryCodeList to the context for thymeleaf
        final String recoveryCodes = "recoveryCodes";
        routingContext.put(recoveryCodes, codes);
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String recoveryCodeUrl = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                routingContext.get(CONTEXT_PATH) + "/mfa/recovery_code", queryParams, true);

        routingContext.put("recoveryCodeURL", recoveryCodeUrl);
        // render the mfa recovery code page
        this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render MFA recovery code page");
    }
}
