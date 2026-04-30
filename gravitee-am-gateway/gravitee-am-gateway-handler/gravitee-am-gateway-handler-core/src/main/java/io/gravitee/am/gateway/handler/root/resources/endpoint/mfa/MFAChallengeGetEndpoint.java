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
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.EnrolledFactorProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE_SENT;
import static io.gravitee.am.common.audit.EventType.MFA_RATE_LIMIT_REACHED;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ALTERNATIVES_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ALTERNATIVES_ENABLE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RATE_LIMIT_ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.VERIFY_ATTEMPT_ERROR_PARAM_KEY;
import static io.gravitee.am.factor.api.FactorContext.KEY_USER;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeGetEndpoint extends MFAChallengeEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeGetEndpoint.class);

    private final ApplicationContext applicationContext;
    private final RateLimiterService rateLimiterService;

    public MFAChallengeGetEndpoint(FactorManager factorManager,
                                   TemplateEngine engine,
                                   ApplicationContext applicationContext,
                                   DomainDataPlane domainDataPlane,
                                   RateLimiterService rateLimiterService,
                                   AuditService auditService) {
        super(factorManager, engine, domainDataPlane, auditService);
        this.applicationContext = applicationContext;
        this.rateLimiterService = rateLimiterService;
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
                logger.warn("User must be authenticated to request MFA challenge.");
                routingContext.fail(401);
                return;
            }
            final Factor factor = getFactor(routingContext, client, endUser);
            final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);
            final String rateLimitError = routingContext.request().getParam(RATE_LIMIT_ERROR_PARAM_KEY);
            final String verifyAttemptsError = routingContext.request().getParam(VERIFY_ATTEMPT_ERROR_PARAM_KEY);
            final String errorDescription = routingContext.request().getParam(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
            final String errorCode = routingContext.request().getParam(ConstantKeys.ERROR_CODE_PARAM_KEY);

            // prepare context
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            routingContext.put(ConstantKeys.FACTOR_KEY, factor);

            if (factor.is(FIDO2)) {
                final String webAuthnLoginPath = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                        routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true);

                routingContext.put("webAuthnLoginPath", webAuthnLoginPath);
                routingContext.put("userName", endUser.getUsername());
            }

            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
            routingContext.put(RATE_LIMIT_ERROR_PARAM_KEY, rateLimitError);
            routingContext.put(VERIFY_ATTEMPT_ERROR_PARAM_KEY, verifyAttemptsError);
            routingContext.put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
            routingContext.put(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode);

            //Include deviceId, so we can show/hide the "save my device" checkbox
            var deviceId = routingContext.session().get(ConstantKeys.DEVICE_ID);
            if (deviceId != null) {
                routingContext.put(ConstantKeys.DEVICE_ID, deviceId);
            }
            if (enableAlternateMFAOptions(client, endUser)) {
                routingContext.put(MFA_ALTERNATIVES_ENABLE_KEY, true);
                routingContext.put(MFA_ALTERNATIVES_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/mfa/challenge/alternatives", queryParams, true));
            }

            final FactorProvider factorProvider = factorManager.get(factor.getId());
            // send challenge
            sendChallenge(factorProvider, routingContext, factor, endUser, resChallenge -> {
                if (resChallenge.failed() && error == null) {
                    logger.error("An error has occurred when sending MFA challenge", resChallenge.cause());
                    routingContext.fail(resChallenge.cause());
                    return;
                }

                Map<String, Object> templateData = generateData(routingContext, domainDataPlane.getDomain(), client);
                if (resChallenge.result() != null) {
                    templateData.put(ENROLLED_FACTOR_KEY, new EnrolledFactorProperties(resChallenge.result()));
                }
                this.renderPage(routingContext, templateData, client, logger, "Unable to render MFA challenge page");
            });
        } catch (Exception ex) {
            logger.error("An error has occurred when rendering MFA challenge page", ex);
            routingContext.fail(503);
        }
    }

    private void sendChallenge(FactorProvider factorProvider, RoutingContext routingContext, Factor factor, User endUser, Handler<AsyncResult<EnrolledFactor>> handler) {
        // create factor context
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final FactorContext factorContext = new FactorContext(applicationContext, new HashMap<>());

        factorContext.getData().putAll(getEvaluableAttributes(routingContext));
        factorContext.registerData(FactorContext.KEY_CLIENT, client);
        factorContext.registerData(KEY_USER, endUser);
        factorContext.registerData(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
        if (!factorProvider.needChallengeSending() || routingContext.get(ConstantKeys.ERROR_PARAM_KEY) != null
                || routingContext.get(RATE_LIMIT_ERROR_PARAM_KEY) != null || routingContext.get(VERIFY_ATTEMPT_ERROR_PARAM_KEY) != null) {
            // do not send challenge in case of error param to avoid useless code generation
            // to provide EnrolledFactor we call the getEnrolledFactor but the overrideMovingFactor is set to false
            // so during the enrollement phase, in case of wrong code provided by the user, the code is not reset
            // otherwise the persisted value will not be the same as the one computed based on the session information
            final EnrolledFactor currentEnrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, false);
            handler.handle(Future.succeededFuture(currentEnrolledFactor));
            return;
        }

        if (rateLimiterService.isRateLimitEnabled()) {
            rateLimiterService.tryConsume(endUser.getId(), factor.getId(), client.getId(), client.getDomain())
                    .subscribe(allowRequest -> {
                                // if sendMessage is allowed, then get the EnrolledFactor with code reinitialiation to manage the movingFactor reset
                                // during enrollment phase (if the user refresh the page)
                                final EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, allowRequest);
                                factorContext.registerData(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
                                if (allowRequest) {
                                    sendChallenge(routingContext, factorProvider, factorContext, endUser, client, factor, handler);
                                } else {
                                    updateAuditLog(routingContext, MFA_RATE_LIMIT_REACHED, endUser, client, factor, factorContext, new Throwable("MFA rate limit reached"));
                                    handleException(routingContext, RATE_LIMIT_ERROR_PARAM_KEY, "mfa_request_limit_exceed");
                                }
                            },
                            error -> handler.handle(Future.failedFuture(error))
                    );
        } else {
            final EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, true);
            factorContext.registerData(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
            sendChallenge(routingContext, factorProvider, factorContext, endUser, client, factor, handler);
        }
    }

    private void sendChallenge(RoutingContext routingContext, FactorProvider factorProvider, FactorContext factorContext, User endUser, Client client, Factor factor, Handler<AsyncResult<EnrolledFactor>> handler) {
        factorProvider.sendChallenge(factorContext)
                .doOnComplete(() -> logger.debug("Challenge sent to user {}", factorContext.getUser().getId()))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, endUser, client, factor, factorContext, null);
                            handler.handle(Future.succeededFuture((EnrolledFactor) factorContext.getData().get(FactorContext.KEY_ENROLLED_FACTOR)));
                        },
                        error -> {
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, endUser, client, factor, factorContext, error);
                            handler.handle(Future.failedFuture(error));

                        }
                );
    }

    private boolean enableAlternateMFAOptions(Client client, io.gravitee.am.model.User endUser) {
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
