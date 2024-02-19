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
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CAN_BE_SKIPPED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
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
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
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
        final MfaFilterContext context = new MfaFilterContext(routingContext, client, factorManager);
        if (hasFactors(client, factorManager)) {
            context.setDefaultFactorWhenApplied(ruleEngine);
            if (stepUpRequired(context, client, ruleEngine)) {
                required(routingContext, flow, context);
            } else if (isEnrollActive(client)) {
                switch (getEnrollSettings(client).getType()) {
                    case OPTIONAL -> optional(routingContext, flow, context);
                    case REQUIRED -> required(routingContext, flow, context);
                    case CONDITIONAL -> conditional(routingContext, flow, client, context);
                }
            } else if (isChallengeActive(client)) {
                enrollIfChallengeRequires(routingContext, flow, client, context);
            } else {
                stop(routingContext, flow);
            }
        } else {
            stop(routingContext, flow);
        }
    }

    private void required(RoutingContext routingContext, AuthenticationFlowChain flow, MfaFilterContext context) {
        if (userHasFactor(context)) {
            continueFlow(routingContext, flow);
        } else {
            enrollment(routingContext, flow);
        }
    }

    private void conditional(RoutingContext routingContext, AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (enrollConditionSatisfied(client, context)) {
            stop(routingContext, flow);
        } else if (userHasFactor(context)) {
            continueFlow(routingContext, flow);
        } else if (canUserSkip(client, context)) {
            routingContext.session().put(MFA_CAN_BE_SKIPPED_KEY, true);
            if (context.isEnrollSkipped()) {
                stopMfaFlow(routingContext, flow);
            } else {
                enrollment(routingContext, flow);
            }
        } else {
            enrollment(routingContext, flow);
        }
    }

    private void optional(RoutingContext routingContext, AuthenticationFlowChain flow, MfaFilterContext context) {
        if (context.isEnrollSkipped()) {
            stop(routingContext, flow);
        } else {
            if (userHasFactor(context)) {
                continueFlow(routingContext, flow);
            } else {
                enrollment(routingContext, flow);
            }
        }
    }

    private void enrollIfChallengeRequires(RoutingContext routingContext, AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
        if (MfaChallengeType.CONDITIONAL.equals(getChallengeSettings(client).getType())) {
            if (challengeConditionSatisfied(client, context, ruleEngine)) {
                stop(routingContext, flow);
            } else {
                enrollment(routingContext, flow);
            }
        } else {
            required(routingContext, flow, context);
        }
    }

    private void enrollment(RoutingContext routingContext, AuthenticationFlowChain flow) {
        executeFlowStep(routingContext, flow, this);
    }

    private boolean enrollConditionSatisfied(Client client, MfaFilterContext context) {
        return evaluateRule(getEnrollSettings(client).getEnrollmentRule(), context, ruleEngine);
    }

    private boolean isEnrollActive(Client client) {
        return ofNullable(client.getMfaSettings()).map(MFASettings::getEnroll).map(EnrollSettings::isActive).orElse(false);
    }

    private boolean userHasFactor(MfaFilterContext context) {
        return context.isEndUserEnrolling() || context.userHasMatchingActivatedFactors();
    }

    private static void continueFlow(RoutingContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_ENROLLMENT_COMPLETED_KEY, true);
        continueMfaFlow(routingContext, flow);
    }

    private static void stop(RoutingContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_ENROLLMENT_COMPLETED_KEY, true);
        stopMfaFlow(routingContext, flow);
    }

    public boolean canUserSkip(Client client, MfaFilterContext context) {
        var enroll = ofNullable(client.getMfaSettings()).map(MFASettings::getEnroll).orElse(new EnrollSettings());
        return enroll.isEnrollmentSkipActive() && evaluateRule(enroll.getEnrollmentSkipRule(), context, ruleEngine);
    }
}
