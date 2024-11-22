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
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.challengeConditionSatisfied;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.continueMfaFlow;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.evaluateRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.executeFlowStep;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getAdaptiveMfaStepUpRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getChallengeSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isChallengeActive;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isMfaFlowStopped;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stepUpRequired;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stopMfaFlow;

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
        if (context.isUserSilentAuth()) {
            stopMfaFlow(context, flow);
            return;
        }
        if (!isMfaFlowStopped(context)) {
            if (context.isUserSelectedEnrollFactor()) {
                challenge(context, flow);
            } else if (!context.isChallengeCompleted(sessionManager) && stepUpRequired(context, client, ruleEngine)) {
                challenge(context, flow);
            } else if (isChallengeActive(client)) {
                switch (getChallengeSettings(client).getType()) {
                    case REQUIRED -> required(context, flow);
                    case CONDITIONAL -> conditional(context, flow, client);
                    case RISK_BASED -> riskBased(context, flow, client);
                }
            } else {
                continueFlow(context, flow);
            }
        } else {
            continueFlow(context, flow);
        }
    }

    private void required(MfaFilterContext context, AuthenticationFlowChain flow) {
        if (context.isUserStronglyAuth() || isRememberDevice(context)) {
            continueFlow(context, flow);
        } else {
            challenge(context, flow);
        }
    }

    private void conditional(MfaFilterContext context, AuthenticationFlowChain flow, Client client) {
        if (challengeConditionSatisfied(client, context, ruleEngine)) {
            continueFlow(context, flow);
        } else if (context.getRememberDeviceSettings().isSkipChallengeWhenRememberDevice()) {
            required(context, flow);
        } else if (context.isUserStronglyAuth()) {
            continueFlow(context, flow);
        } else {
            challenge(context, flow);
        }
    }

    private void riskBased(MfaFilterContext context, AuthenticationFlowChain flow, Client client) {
        if (context.isUserStronglyAuth() || isSafe(context, client)) {
            continueFlow(context, flow);
        } else {
            challenge(context, flow);
        }
    }

    private void challenge(MfaFilterContext mfaFilterContext, AuthenticationFlowChain flow) {
        final var sessionState = sessionManager.getSessionState(mfaFilterContext.routingContext());
        sessionState.getMfaState().challengeOngoing();
        sessionState.save(mfaFilterContext.session());

        executeFlowStep(mfaFilterContext, flow, this);
    }

    private boolean isSafe(MfaFilterContext context, Client client) {
        return evaluateRule(getAdaptiveMfaStepUpRule(client), context, ruleEngine);
    }

    private boolean isRememberDevice(MfaFilterContext context) {
        return !context.isDeviceRiskAssessmentEnabled() && context.getRememberDeviceSettings().isActive() && context.deviceAlreadyExists();
    }

    private static void continueFlow(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        continueMfaFlow(routingContext, flow);
    }
}

