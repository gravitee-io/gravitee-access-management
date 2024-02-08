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
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.challengeConditionSatisfied;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.continueFlow;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.evaluateRule;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getChallengeSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.getEnrollSettings;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.hasFactor;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.isChallengeActive;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stepUp;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.redirect;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.stop;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
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
        if (hasFactor(client, factorManager)) {
            if (stepUp(context, client, ruleEngine)) {
                required(routingContext, flow, context);
            } else if (isEnrollActive(client)) {
                switch (getEnrollSettings(client).getType()) {
                    case OPTIONAL -> optional(routingContext, flow, client, context);
                    case REQUIRED -> required(routingContext, flow, context);
                    case CONDITIONAL -> conditional(routingContext, flow, client, context);
                }
            } else if (isChallengeActive(client)) {
                if (MfaChallengeType.CONDITIONAL.equals(getChallengeSettings(client).getType()) ) {
                    if (challengeConditionSatisfied(client, context, ruleEngine)) {
                        stop(routingContext, flow);
                    } else {
                        enrollment(routingContext, flow);
                    }
                } else {
                    required(routingContext, flow, context);
                }
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
        } else { //todo AM-1140 skip conditional not implemented
            enrollment(routingContext, flow);
        }
    }

    private void optional(RoutingContext routingContext, AuthenticationFlowChain flow, Client client, MfaFilterContext context) {
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

    private void enrollment(RoutingContext routingContext, AuthenticationFlowChain flow) {
        redirect(routingContext, flow, this);
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

    public boolean canUserSkip(Client client, MfaFilterContext context) {
        var enrollRule = ofNullable(client.getMfaSettings()).map(MFASettings::getEnroll).map(EnrollSettings::getEnrollmentRule).orElse(null);
        return evaluateRule(enrollRule, context, ruleEngine);
    }
}
