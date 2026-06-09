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

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;

/**
 * Resends an MFA challenge code (SMS, email, call, etc.)
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeSendEndpoint extends MFAChallengeEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeSendEndpoint.class);
    private static final String SEND_CHALLENGE_FAILED = "send_challenge_failed";
    private static final String SEND_CHALLENGE_FAILED_DESCRIPTION = "Unable to send a new code, please try again later";

    public MFAChallengeSendEndpoint(FactorManager factorManager,
                                    TemplateEngine engine,
                                    ApplicationContext applicationContext,
                                    DomainDataPlane domainDataPlane,
                                    RateLimiterService rateLimiterService,
                                    AuditService auditService) {
        super(factorManager, engine, applicationContext, domainDataPlane, rateLimiterService, auditService);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        resendMfaChallenge(routingContext);
    }

    private void resendMfaChallenge(RoutingContext routingContext) {
        try {
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final User endUser = user(routingContext);
            if (endUser == null) {
                logger.warn("User must be authenticated to resend MFA challenge.");
                respondJsonChallengeFailure(routingContext, SEND_CHALLENGE_FAILED, "User must be authenticated", HttpStatusCode.UNAUTHORIZED_401);
                return;
            }
            final Factor factor = getFactor(routingContext, client, endUser);
            final FactorProvider factorProvider = factorManager.get(factor.getId());
            if (!factorProvider.needChallengeSending()) {
                respondJsonChallengeFailure(routingContext, SEND_CHALLENGE_FAILED, "Invalid factor", HttpStatusCode.BAD_REQUEST_400);
                return;
            }

            sendMfaChallenge(factorProvider, routingContext, factor, endUser, true, true, resChallenge -> {
                if (resChallenge.failed()) {
                    logger.error("An error has occurred when resending MFA challenge", resChallenge.cause());
                    respondJsonChallengeFailure(routingContext, SEND_CHALLENGE_FAILED, SEND_CHALLENGE_FAILED_DESCRIPTION, HttpStatusCode.SERVICE_UNAVAILABLE_503);
                    return;
                }
                respondJsonChallengeSuccess(routingContext);
            });
        } catch (Exception ex) {
            logger.error("An error has occurred when resending MFA challenge", ex);
            respondJsonChallengeFailure(routingContext, SEND_CHALLENGE_FAILED, "Unexpected error occurred", HttpStatusCode.SERVICE_UNAVAILABLE_503);
        }
    }

    private void respondJsonChallengeSuccess(RoutingContext routingContext) {
        respondJson(routingContext, HttpStatusCode.OK_200, new JsonObject().put("success", true), null);
    }

    private void respondJsonChallengeFailure(RoutingContext routingContext, String errorCode, String errorDescription, int statusCode) {
        respondJson(routingContext, statusCode, new JsonObject()
                .put(ConstantKeys.ERROR_PARAM_KEY, ConstantKeys.MFA_CHALLENGE_FAILED)
                .put(ConstantKeys.ERROR_CODE_PARAM_KEY, errorCode)
                .put(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription), errorDescription);
    }
}
