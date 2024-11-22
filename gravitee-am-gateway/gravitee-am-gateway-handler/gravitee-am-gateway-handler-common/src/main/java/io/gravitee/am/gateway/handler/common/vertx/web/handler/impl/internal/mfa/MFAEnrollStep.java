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
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.challengeConditionSatisfied;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.continueMfaFlow;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.evaluateRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.executeFlowStep;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getChallengeSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getEnrollSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.hasFactors;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isChallengeActive;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stepUpRequired;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stopMfaFlow;
import static java.util.Optional.ofNullable;

public class MFAEnrollStep extends MFAStep {

    private final FactorManager factorManager;

    public MFAEnrollStep(Handler<RoutingContext> wrapper, RuleEngine ruleEngine, FactorManager factorManager) {
        super(wrapper, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final MfaFilterContext context = new MfaFilterContext(routingContext, client, factorManager, ruleEngine);
        if (context.isUserSilentAuth()) {
            stop(context, flow);
            return;
        }
        if (hasFactors(client, factorManager)) {
            if (context.isFactorSelected() && !context.checkSelectedFactor()) {
                enrollment(context, flow);
            } else if (!userHasFactor(context) && stepUpRequired(context, client, ruleEngine)) {
                enrollment(context, flow);
            } else if (isEnrollActive(client)) {
                switch (getEnrollSettings(client).getType()) {
                    case OPTIONAL -> optional(flow, context);
                    case REQUIRED -> required(flow, context);
                    case CONDITIONAL -> conditional(flow, client, context);
                }
            } else if (stepUpRequired(context, client, ruleEngine)) {
                continueFlow(context, flow);
            } else if (isChallengeActive(client)) {
                enrollIfChallengeRequires(flow, client, context);
            } else {
                stop(context, flow);
            }
        } else {
            stop(context, flow);
        }
    }

    private void required(AuthenticationFlowChain flow, MfaFilterContext context) {
        if (userHasFactor(context)) {
            continueFlow(context, flow);
        } else {
            enrollment(context, flow);
        }
    }

    private void conditional(AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (enrollConditionSatisfied(client, context)) {
            stop(context, flow);
        } else if (userHasFactor(context)) {
            continueFlow(context, flow);
        } else if (canUserSkip(client, context)) {
            context.session().put(MFA_ENROLL_CONDITIONAL_SKIPPED_KEY, true);

            final var sessionState = sessionManager.getSessionState(context.routingContext());
            sessionState.getMfaState().enrollConditionalSkip();
            sessionState.save(context.session());

            if (context.isEnrollSkipped()) {
                stopMfaFlow(context, flow);
            } else {
                enrollment(context, flow);
            }
        } else {
            enrollment(context, flow);
        }
    }

    private void optional(AuthenticationFlowChain flow, MfaFilterContext context) {
        if (context.isEnrollSkipped()) {
            stop(context, flow);
        } else {
            if (userHasFactor(context)) {
                continueFlow(context, flow);
            } else {
                enrollment(context, flow);
            }
        }
    }

    private void enrollIfChallengeRequires(AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (MfaChallengeType.CONDITIONAL.equals(getChallengeSettings(client).getType())) {
            if (challengeConditionSatisfied(client, context, ruleEngine)) {
                stop(context, flow);
            } else {
                required(flow, context);
            }
        } else {
            required(flow, context);
        }
    }

    private void enrollment(MfaFilterContext mfaFilterContext, AuthenticationFlowChain flow) {
        final var sessionState = sessionManager.getSessionState(mfaFilterContext.routingContext());
        sessionState.getMfaState().enrollmentOngoing();
        sessionState.save(mfaFilterContext.session());
        executeFlowStep(mfaFilterContext, flow, this);
    }

    private boolean enrollConditionSatisfied(Client client, MfaFilterContext context) {
        return evaluateRule(getEnrollSettings(client).getEnrollmentRule(), context, ruleEngine);
    }

    private boolean isEnrollActive(Client client) {
        return ofNullable(client.getMfaSettings()).map(MFASettings::getEnroll).map(EnrollSettings::isActive).orElse(false);
    }

    private boolean userHasFactor(MfaFilterContext context) {
        return context.isUserSelectedEnrollFactor() || context.userHasMatchingActivatedFactors();
    }

    private static void continueFlow(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        continueMfaFlow(routingContext, flow);
    }

    private static void stop(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        stopMfaFlow(routingContext, flow);
    }

    public boolean canUserSkip(Client client, MfaFilterContext context) {
        var enroll = ofNullable(client.getMfaSettings()).map(MFASettings::getEnroll).orElse(new EnrollSettings());
        return enroll.isEnrollmentSkipActive() && evaluateRule(enroll.getEnrollmentSkipRule(), context, ruleEngine);
    }
}
