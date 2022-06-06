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
public class StepUpAuthenticationFilter extends MfaContextHolder implements Supplier<Boolean> {

    private final RuleEngine ruleEngine;

    public StepUpAuthenticationFilter(
            MfaFilterContext context,
            RuleEngine ruleEngine) {
        super(context);
        this.ruleEngine = ruleEngine;
    }

    @Override
    public Boolean get() {
        // If Adaptive MFA is active and KO (rule == false) we bypass this filter
        // Because it could return true and skip MFA
        if (!context.isMfaSkipped() && context.isAmfaActive() && !context.isAmfaRuleTrue()) {
            return false;
        }
        String mfaStepUpRule = context.getStepUpRule();
        if (context.isStepUpActive()) {
            context.setStepUpRuleTrue(isStepUpAuthentication(mfaStepUpRule));
        }
        return !context.isAmfaActive() &&
                context.isStepUpActive() &&
                !context.isStepUpRuleTrue() &&
                (context.isUserStronglyAuth() || context.isMfaSkipped());
    }

    protected boolean isStepUpAuthentication(String selectionRule) {
        return ruleEngine.evaluate(selectionRule, context.getEvaluableContext(), Boolean.class, false);
    }
}
