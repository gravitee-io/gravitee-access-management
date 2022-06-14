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
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AdaptiveMfaFilter extends MfaContextHolder implements Supplier<Boolean> {

    private final RuleEngine ruleEngine;

    public AdaptiveMfaFilter(
            MfaFilterContext context,
            RuleEngine ruleEngine) {
        super(context);
        this.ruleEngine = ruleEngine;
    }

    @Override
    public Boolean get() {
        if (!context.isAmfaActive()) {
            return false;
        }

        if (context.isMfaSkipped() && !context.hasEndUserAlreadyEnrolled() && !context.userHasMatchingFactors()) {
            return false;
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
}
