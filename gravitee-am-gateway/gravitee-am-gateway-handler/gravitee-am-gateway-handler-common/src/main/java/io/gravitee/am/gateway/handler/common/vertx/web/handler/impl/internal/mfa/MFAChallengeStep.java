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
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_CONDITIONAL_SKIPPED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_DEVICE_SKIP_UNTIL;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
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
import java.time.Instant;
import static java.util.Objects.nonNull;

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
        if (!isMfaFlowStopped(context)) {
            if (stepUpRequired(context, client, ruleEngine) || context.isEndUserEnrolling()) {
                challenge(context, flow);
            } else if (isChallengeActive(client)) {
                switch (getChallengeSettings(client).getType()) {
                    case REQUIRED -> required(context, flow, client);
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

    private void required(MfaFilterContext context, AuthenticationFlowChain flow, Client client) {
        if (context.isChallengeOnceCompleted() && isRememberDeviceOrSkipped(context, client)) {
            continueFlow(context, flow);
        } else {
            challenge(context, flow);
        }
    }

    private void conditional(MfaFilterContext context, AuthenticationFlowChain flow, Client client) {
        if (challengeConditionSatisfied(client, context, ruleEngine)) {
            context.session().put(MFA_CHALLENGE_CONDITIONAL_SKIPPED_KEY, true);
            continueFlow(context, flow);
        } else {
            required(context, flow, client);
        }
    }

    private void riskBased(MfaFilterContext context, AuthenticationFlowChain flow, Client client) {
        if (isSafe(context, client)) {
            continueFlow(context, flow);
        } else {
            required(context, flow, client);
        }
    }

    private void challenge(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        executeFlowStep(routingContext, flow, this);
    }

    private boolean isSafe(MfaFilterContext context, Client client) {
        return !evaluateRule(getAdaptiveMfaStepUpRule(client), context, ruleEngine);
    }

    private boolean isRememberDeviceOrSkipped(MfaFilterContext context, Client client) {
        return !context.isDeviceRiskAssessmentEnabled()
                && (!context.getRememberDeviceSettings().isActive() || context.deviceAlreadyExists() || isUserSkip(context) || MfaUtils.isSkipRememberDevice(context.routingContext(), client));
    }

    private static void continueFlow(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_CHALLENGE_COMPLETED_KEY, true);
        continueMfaFlow(routingContext, flow);
    }

    private boolean isUserSkip(MfaFilterContext context) {
        Instant instant = context.session().get(REMEMBER_DEVICE_SKIP_UNTIL);
        return nonNull(instant) && instant.isAfter(Instant.now());
    }
}

