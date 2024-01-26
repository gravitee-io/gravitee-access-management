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

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_CONDITION_SATISFIED;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_CONDITION_SATISFIED;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_USER_ENROLLING;
import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeStep extends MFAStep {
    private static final boolean SAFE = true;
    private static final boolean UNSAFE = false;

    private final FactorManager factorManager;

    public MFAChallengeStep(Handler<RoutingContext> wrapper, RuleEngine ruleEngine, FactorManager factorManager) {
        super(wrapper, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        var context = new MfaFilterContext(routingContext, client, factorManager);

        final boolean skipMFA = isNull(client) || noFactor(client) || context.isMfaChallengeComplete()
                || isAdaptiveMfa(context) || isStepUp(context) || isRememberDevice(context) || isEnrollSkipRuleSatisfied(routingContext)
                || isStronglyAuthenticated(context) /*|| isUserStronglyAuthenticated(context)*/
                || skipIfChallengeInactiveOrUserNotEnrolling(client, routingContext) || isChallengeSkipRuleSatisfied(routingContext);

        if (skipMFA) {
            flow.doNext(routingContext);
        } else {
            flow.exit(this);
        }
    }

    private boolean noFactor(Client client) {
        final Set<String> factors = client.getFactors();
        return isNull(factors) || factors.isEmpty() || onlyRecoveryCodeFactor(factors);
    }

    private boolean onlyRecoveryCodeFactor(Set<String> factors) {
        if (factors.size() == 1) {
            final String factorId = factors.stream().findFirst().get();
            final Factor factor = factorManager.getFactor(factorId);
            return factor.getFactorType().equals(FactorType.RECOVERY_CODE);
        }
        return false;
    }

    private boolean isAdaptiveMfa(MfaFilterContext context) {
        if (!context.isAmfaActive()) {
            return false;
        }

        if (context.isMfaSkipped() && !context.hasEndUserAlreadyEnrolled() && !context.userHasMatchingFactors()) {
            return false;
        }

        // We make sure that the rule can be triggered if we come from an already enrolled user
        // And that the user is not trying to challenge to an alternative factor
        if (context.userHasMatchingActivatedFactors() && !context.hasUserChosenAlternativeFactor()) {
            // We are retaining the value since other features will use it in the chain
            context.setAmfaRuleTrue(ruleEngine.evaluate(context.getAmfaRule(), context.getEvaluableContext(), Boolean.class, false));
        }

        // If one of the other filter is active. We want to make sure that
        // if Adaptive MFA skips (rule == true) we want other MFA methods to trigger
        var rememberDevice = context.getRememberDeviceSettings();
        return !rememberDevice.isActive() && !context.isStepUpActive() && context.isAmfaRuleTrue();
    }

    private boolean isStepUp(MfaFilterContext context) {
        // If Adaptive MFA is active and KO (rule == false) we bypass this filter
        // Because it could return true and skip MFA
        if (!context.isMfaSkipped() && context.isAmfaActive() && !context.isAmfaRuleTrue()) {
            return false;
        }
        String mfaStepUpRule = context.getStepUpRule();
        if (context.isStepUpActive()) {
            context.setStepUpRuleTrue(isStepUpAuthentication(mfaStepUpRule, context));
        }
        return !context.isAmfaActive() &&
                context.isStepUpActive() &&
                !context.isStepUpRuleTrue() &&
                (context.isUserStronglyAuth() || context.isMfaSkipped());
    }

    private boolean isStepUpAuthentication(String selectionRule, MfaFilterContext context) {
        return ruleEngine.evaluate(selectionRule, context.getEvaluableContext(), Boolean.class, false);
    }

    private boolean isRememberDevice(MfaFilterContext context) {
        // If Adaptive MFA is active and KO (rule == false) we bypass this filter
        // Because the device could be known and skip MFA
        final boolean mfaSkipped = context.isMfaSkipped();
        var rememberDeviceSettings = context.getRememberDeviceSettings();

        if (!mfaSkipped && context.isAmfaActive()) {
            if (context.isAmfaRuleTrue() && rememberDeviceSettings.isSkipRememberDevice()) {
                return SAFE;
            } else if (!context.isAmfaRuleTrue()) {
                return UNSAFE;
            }
        }

        // Step up might be active
        final boolean userStronglyAuth = context.isUserStronglyAuth();
        if (context.isStepUpActive() && (userStronglyAuth || mfaSkipped)) {
            return UNSAFE;
        }

        // We don't want device risk assessment to interfere
        if (context.isDeviceRiskAssessmentEnabled()) {
            return UNSAFE;
        }

        return context.userHasMatchingActivatedFactors() && rememberDeviceSettings.isActive() && context.deviceAlreadyExists();
    }

    private boolean isUserStronglyAuthenticated(MfaFilterContext context) {
        return context.isUserStronglyAuth();
    }

    private boolean isStronglyAuthenticated(MfaFilterContext context) {
        final boolean userStronglyAuth = context.isUserStronglyAuth();
        final boolean mfaSkipped = context.isMfaSkipped();
        //If user has not matching activated factors, we enforce MFA
        if (!mfaSkipped && !context.userHasMatchingActivatedFactors()) {
            return false;
        }
        // We need to check whether the AMFA, Device and Step Up rule is false since we don't know of other MFA return False
        else if (
            // Whether Adaptive MFA is not true
                !mfaSkipped && context.isAmfaActive() && !context.isAmfaRuleTrue() ||
                        // Or We don't remember the device and there is
                        // no device assessment active and that mfa is not skipped
                        !context.isDeviceRiskAssessmentEnabled() && !mfaSkipped && context.getRememberDeviceSettings().isActive() && !context.deviceAlreadyExists() ||
                        // Or that Step up authentication is active and user is strongly auth or mfa is skipped
                        context.isStepUpActive() && context.isStepUpRuleTrue() && (userStronglyAuth || mfaSkipped)) {
            return false;
        } else if (
            // We need to make sure we come from a place where the user is not trying to challenge a new device
            // AND Adaptive MFA may be active and may return true, we return true
                context.userHasMatchingActivatedFactors()
                        && !context.hasUserChosenAlternativeFactor()
                        && context.isAmfaActive() && context.isAmfaRuleTrue()
        ) {
            return true;
        }
        // We check then if StepUp is not active and of user is strongly auth or mfa is skipped to skip MFA
        return !context.isStepUpActive() && (userStronglyAuth || mfaSkipped);
    }

    private boolean isEnrollSkipRuleSatisfied(RoutingContext context) {
        return Optional.ofNullable((Boolean)context.session().get(MFA_ENROLLMENT_CONDITION_SATISFIED)).orElse(Boolean.FALSE);
    }

    private boolean isChallengeSkipRuleSatisfied(RoutingContext context) {
        return Optional.ofNullable((Boolean)context.session().get(MFA_CHALLENGE_CONDITION_SATISFIED)).orElse(Boolean.FALSE);
    }

    private boolean isUserEnrolling(RoutingContext context) {
        return Optional.ofNullable((Boolean)context.session().get(MFA_ENROLLMENT_USER_ENROLLING)).orElse(Boolean.FALSE);
    }

    private boolean skipIfChallengeInactiveOrUserNotEnrolling(Client client, RoutingContext context) {
        if (isUserEnrolling(context) || MfaUtils.isChallengeActive(client)) {
            return false;
        } else {
            return true;
        }
    }
}
