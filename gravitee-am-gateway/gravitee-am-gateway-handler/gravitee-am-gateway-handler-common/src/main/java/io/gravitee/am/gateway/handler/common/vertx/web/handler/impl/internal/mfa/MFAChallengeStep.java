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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa;

import io.gravitee.am.common.utils.ConstantKeys;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.challengeConditionSatisfied;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.continueMfaFlow;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.evaluateRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.executeFlowStep;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getAdaptiveMfaStepUpRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getChallengeSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isChallengeActive;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isMfaFlowStopped;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stepUpRequired;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class MFAChallengeStep extends MFAStep {
    private final FactorManager factorManager;

    public MFAChallengeStep(Handler<RoutingContext> wrapper, RuleEngine ruleEngine, FactorManager factorManager) {
        super(wrapper, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final MfaFilterContext context = new MfaFilterContext(routingContext, client, factorManager, ruleEngine);
        if (!isMfaFlowStopped(routingContext)) {
            if (stepUpRequired(context, client, ruleEngine) || context.isEndUserEnrolling()) {
                challenge(routingContext, flow);
            } else if (isChallengeActive(client)) {
                switch (getChallengeSettings(client).getType()) {
                    case REQUIRED -> required(routingContext, flow, context);
                    case CONDITIONAL -> conditional(routingContext, flow, client, context);
                    case RISK_BASED -> riskBased(routingContext, flow, client, context);
                }
            } else {
                continueFlow(routingContext, flow);
            }
        } else {
            continueFlow(routingContext, flow);
        }
    }

    private void required(RoutingContext routingContext, AuthenticationFlowChain flow, MfaFilterContext context) {
        if (context.isChallengeOnceCompleted() && isRememberDeviceOrSkipped(context)) {
            continueFlow(routingContext, flow);
        } else {
            challenge(routingContext, flow);
        }
    }

    private void conditional(RoutingContext routingContext, AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (context.isChallengeOnceCompleted() || challengeConditionSatisfied(client, context, ruleEngine)) {
            continueFlow(routingContext, flow);
        } else {
            challenge(routingContext, flow);
        }
    }

    private void riskBased(RoutingContext routingContext, AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (context.isChallengeOnceCompleted() || isSafe(client, context)) {
            continueFlow(routingContext, flow);
        } else {
            challenge(routingContext, flow);
        }
    }

    private void challenge(RoutingContext routingContext, AuthenticationFlowChain flow) {
        executeFlowStep(routingContext, flow, this);
    }

    private boolean isSafe(Client client, MfaFilterContext context) {
        return !evaluateRule(getAdaptiveMfaStepUpRule(client), context, ruleEngine);
    }

    private boolean isRememberDeviceOrSkipped(MfaFilterContext context) {
        return !context.isDeviceRiskAssessmentEnabled()
                && (!context.getRememberDeviceSettings().isActive() || (context.deviceAlreadyExists() || context.getRememberDeviceSettings().isSkipRememberDevice()));
    }

    private static void continueFlow(RoutingContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_CHALLENGE_COMPLETED_KEY, true);
        continueMfaFlow(routingContext, flow);
    }
}

