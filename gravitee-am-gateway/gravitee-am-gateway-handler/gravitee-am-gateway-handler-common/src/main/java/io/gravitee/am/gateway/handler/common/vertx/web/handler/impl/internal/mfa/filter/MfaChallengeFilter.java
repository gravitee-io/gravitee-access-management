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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.filter;


import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;

import java.util.function.Supplier;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaChallengeFilter extends MfaContextHolder implements Supplier<Boolean> {
    private final RuleEngine ruleEngine;

    public MfaChallengeFilter(MfaFilterContext context, RuleEngine ruleEngine) {
        super(context);
        this.ruleEngine = ruleEngine;
    }

    @Override
    public Boolean get() {
        // challenge processed, skip it
        if (context.isMfaChallengeComplete()) {
            return true;
        }

        // skip mfa Challenge if user authenticated and step is disabled
        if (context.isUserFullyAuthenticated()) {
            String mfaStepUpRule = context.getStepUpRule();
            return !context.isStepUpActive() || (context.isStepUpActive() && !isStepUpAuthentication(mfaStepUpRule));
        } else {
            final var adaptiveMfa = evaluateAdaptiveMfa();
            final var rememberDevice = evaluateRememberDevice();
            final var strongAuth = evaluateStrongAuth();

            return adaptiveMfa || rememberDevice || strongAuth;
        }
    }

    private Boolean evaluateAdaptiveMfa() {
        // user not yet fully authenticated, check MFA challenge execution
        if (!context.isAmfaActive()) {
            return false;
        }

        if (context.isMfaSkipped() && !context.hasEndUserAlreadyEnrolled() && !context.userHasMatchingFactors()) {
            return false;
        }

        // user already fully authenticated and StepUp is disabled
        if (context.isUserFullyAuthenticated() && !context.isStepUpActive() &&
                !context.isStepUpRuleTrue()){
            return true;
        }

        // We make sure that the rule can be triggered if we come from an already enrolled user
        // And that the user is not trying to challenge to an alternative factor
        if (context.userHasMatchingActivatedFactors() && !context.hasUserChosenAlternativeFactor()){
            // We are retaining the value since other features will use it in the chain
            context.setAmfaRuleTrue(ruleEngine.evaluate(context.getAmfaRule(), context.getEvaluableContext(), Boolean.class, false));
        }

        // If one of the other filter is active. We want to make sure that
        // if Adaptive MFA skips (rule == true) we want other MFA methods to trigger
        var rememberDevice = context.getRememberDeviceSettings();
        return !rememberDevice.isActive() && !context.isStepUpActive() && context.isAmfaRuleTrue();
    }

    private Boolean evaluateRememberDevice() {
        // If Adaptive MFA is active and KO (rule == false) we bypass this filter
        // Because the device could be known and skip MFA
        final boolean mfaSkipped = context.isMfaSkipped();

        // We might want to evaluate Adaptive MFA
        var rememberDeviceSettings = context.getRememberDeviceSettings();
        if (!mfaSkipped && context.isAmfaActive() && !context.isAmfaRuleTrue()) {
            if ((context.isUserStronglyAuth() && !context.isStepUpActive())
                    || (!context.isUserStronglyAuth() && rememberDeviceSettings.isSkipRememberDevice() && context.deviceAlreadyExists())) {
                return true;
            }
            return false;
        }

        // Step up might be active
        final boolean userStronglyAuth = context.isUserStronglyAuth();
        if (context.isStepUpActive() && (userStronglyAuth || mfaSkipped)) {
            return false;
        }

        // We don't want device risk assessment to interfere
        if (context.isDeviceRiskAssessmentEnabled()) {
            return false;
        }

        return context.userHasMatchingActivatedFactors() && rememberDeviceSettings.isActive() && (context.deviceAlreadyExists() || rememberDeviceSettings.isSkipRememberDevice());
    }

    public Boolean evaluateStrongAuth() {
        final boolean userStronglyAuth = context.isUserStronglyAuth();
        final boolean mfaSkipped = context.isMfaSkipped();
        //If user has not matching activated factors, we enforce MFA
        if (!mfaSkipped && !context.userHasMatchingActivatedFactors()){
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
    protected boolean isStepUpAuthentication(String selectionRule) {
        return ruleEngine.evaluate(selectionRule, context.getEvaluableContext(), Boolean.class, false);
    }
}
