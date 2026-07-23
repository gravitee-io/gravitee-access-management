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
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.EnrolledFactorProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ALTERNATIVES_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ALTERNATIVES_ENABLE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_RESEND_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_RESEND_ENABLED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RATE_LIMIT_ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.VERIFY_ATTEMPT_ERROR_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import lombok.CustomLog;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class MFAChallengeGetEndpoint extends MFAChallengeEndpoint {


    public MFAChallengeGetEndpoint(FactorManager factorManager,
                                   TemplateEngine engine,
                                   ApplicationContext applicationContext,
                                   DomainDataPlane domainDataPlane,
                                   RateLimiterService rateLimiterService,
                                   AuditService auditService) {
        super(factorManager, engine, applicationContext, domainDataPlane, rateLimiterService, auditService);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        renderMFAPage(routingContext);
    }

    private void renderMFAPage(RoutingContext routingContext) {
        try {
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final User endUser = user(routingContext);
            if (endUser == null) {
                log.warn("User must be authenticated to request MFA challenge.");
                routingContext.fail(401);
                return;
            }
            final Factor factor = getFactor(routingContext, client, endUser);
            final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);
            final String rateLimitError = routingContext.request().getParam(RATE_LIMIT_ERROR_PARAM_KEY);
            final String verifyAttemptsError = routingContext.request().getParam(VERIFY_ATTEMPT_ERROR_PARAM_KEY);
            final String errorDescription = routingContext.request().getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
            final String errorCode = routingContext.request().getParam(ConstantKeys.ERROR_CODE_PARAM_KEY);

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            routingContext.put(ConstantKeys.FACTOR_KEY, factor);

            if (factor.is(FIDO2)) {
                final String webAuthnLoginPath = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                        routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true);

                routingContext.put("webAuthnLoginPath", webAuthnLoginPath);
                if (Boolean.TRUE.equals(routingContext.get(ConstantKeys.WEBAUTHN_CLIENT_ERROR_REPORTING_ENABLED_KEY))) {
                    final String webAuthnErrorPath = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                            routingContext.get(CONTEXT_PATH) + "/webauthn/webauthn-error", queryParams, true);
                    routingContext.put("webAuthnErrorPath", webAuthnErrorPath);
                }
                routingContext.put("userName", endUser.getUsername());
            }

            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
            routingContext.put(RATE_LIMIT_ERROR_PARAM_KEY, rateLimitError);
            routingContext.put(VERIFY_ATTEMPT_ERROR_PARAM_KEY, verifyAttemptsError);
            routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
            routingContext.put(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode);

            var deviceId = routingContext.session().get(ConstantKeys.DEVICE_ID);
            if (deviceId != null) {
                routingContext.put(ConstantKeys.DEVICE_ID, deviceId);
            }
            if (enableAlternateMFAOptions(client, endUser)) {
                routingContext.put(MFA_ALTERNATIVES_ENABLE_KEY, true);
                routingContext.put(MFA_ALTERNATIVES_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/mfa/challenge/alternatives", queryParams, true));
            }

            final FactorProvider factorProvider = factorManager.get(factor.getId());
            if (factorProvider.needChallengeSending()) {
                routingContext.put(MFA_CHALLENGE_RESEND_ENABLED_KEY, true);
                routingContext.put(MFA_CHALLENGE_RESEND_ACTION_KEY,
                        resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/mfa/challenge/send", queryParams, true));
            }

            sendMfaChallenge(factorProvider, routingContext, factor, endUser, false, false, resChallenge -> {
                if (resChallenge.failed() && error == null) {
                    log.error("An error has occurred when sending MFA challenge", resChallenge.cause());
                    routingContext.fail(resChallenge.cause());
                    return;
                }

                Map<String, Object> templateData = generateData(routingContext, domainDataPlane.getDomain(), client);
                if (resChallenge.result() != null) {
                    templateData.put(ENROLLED_FACTOR_KEY, new EnrolledFactorProperties(resChallenge.result()));
                }
                this.renderPage(routingContext, templateData, client, log, "Unable to render MFA challenge page");
            });
        } catch (Exception ex) {
            log.error("An error has occurred when rendering MFA challenge page", ex);
            routingContext.fail(503);
        }
    }

    private boolean enableAlternateMFAOptions(Client client, User endUser) {
        if (endUser.getFactors() == null || endUser.getFactors().size() <= 1) {
            return false;
        }
        if (client.getFactorSettings() == null || client.getFactorSettings().getApplicationFactors() == null || client.getFactorSettings().getApplicationFactors().size() <= 1) {
            return false;
        }
        final Set<String> clientFactorIds = client.getFactors();
        return endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> factorManager.get(enrolledFactor.getFactorId()) != null)
                .filter(enrolledFactor -> clientFactorIds.contains(enrolledFactor.getFactorId()))
                .count() > 1L;
    }
}
