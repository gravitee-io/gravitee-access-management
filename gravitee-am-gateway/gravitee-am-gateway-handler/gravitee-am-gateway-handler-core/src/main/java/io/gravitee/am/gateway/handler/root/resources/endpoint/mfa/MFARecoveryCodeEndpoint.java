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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
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

    public MFARecoveryCodeEndpoint(TemplateEngine templateEngine, Domain domain, UserService userService) {
        super(templateEngine);
        this.domain = domain;
        this.userService = userService;
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
                                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                                    final String returnUrl = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                                            routingContext.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);

                                    doRedirect(routingContext, returnUrl);
                                },
                                routingContext::fail
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
            final Optional<EnrolledFactorSecurity> factorSecurity = userEnrolledFactorSecurity(endUser);
            if (factorSecurity.isPresent()) {
                final List<String> recoveryCodeList = (List<String>) factorSecurity.get().getAdditionalData().get(RECOVERY_CODE);
                //add recoveryCodeList to the context for thymeleaf
                final String recoveryCodes = "recoveryCodes";
                routingContext.put(recoveryCodes, recoveryCodeList);
            }

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String recoveryCodeUrl = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                    routingContext.get(CONTEXT_PATH) + "/mfa/recovery_code", queryParams, true);

            routingContext.put("recoveryCodeURL", recoveryCodeUrl);
            // render the mfa recovery code page
            this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render MFA recovery code page");

        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA recovery code page", ex);
            routingContext.fail(503);
        }
    }

    private Optional<EnrolledFactorSecurity> userEnrolledFactorSecurity(User endUser) {
        return endUser.getFactors()
                .stream()
                .filter(ftr -> ftr.getSecurity() != null && ftr.getSecurity().getType().equals(RECOVERY_CODE))
                .map(EnrolledFactor::getSecurity)
                .findFirst();
    }

    private void doRedirect(RoutingContext routingContext, String url) {
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, url)
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
}
