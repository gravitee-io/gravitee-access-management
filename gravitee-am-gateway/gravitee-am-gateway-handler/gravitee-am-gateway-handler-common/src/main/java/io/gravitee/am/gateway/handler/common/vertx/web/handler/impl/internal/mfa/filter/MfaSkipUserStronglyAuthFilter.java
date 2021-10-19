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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;

import java.util.function.Supplier;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaSkipUserStronglyAuthFilter extends MfaContextHolder implements Supplier<Boolean> {


    public MfaSkipUserStronglyAuthFilter(MfaFilterContext context) {
        super(context);
    }

    @Override
    public Boolean get() {
        final boolean userStronglyAuth = context.isUserStronglyAuth();
        // We need to check whether the AMFA, Device and Step Up rule is false since we don't know of other MFA return False
        final boolean mfaSkipped = context.isMfaSkipped();
        if (    // Whether Adaptive MFA is not true
                context.isAmfaActive() && !context.isAmfaRuleTrue() ||
                // Or We don't remember the device and that mfa is not skipped
                context.getRememberDeviceSettings().isActive() && !context.deviceAlreadyExists() && !mfaSkipped ||
                // Or that Step up authentication is active and user is strongly auth or mfa is skipped
                context.isStepUpActive() && context.isStepUpRuleTrue() && (userStronglyAuth || mfaSkipped)) {
            return false;
        } else if (
                // Adaptive MFA may be active and may return true, we return true
                context.isAmfaActive() && context.isAmfaRuleTrue()
        ) {
            return true;
        }
        // We check then if StepUp is not active and of user is strongly auth or mfa is skipped to skip MFA
        return !context.isStepUpActive() && (userStronglyAuth || mfaSkipped);
    }
}
