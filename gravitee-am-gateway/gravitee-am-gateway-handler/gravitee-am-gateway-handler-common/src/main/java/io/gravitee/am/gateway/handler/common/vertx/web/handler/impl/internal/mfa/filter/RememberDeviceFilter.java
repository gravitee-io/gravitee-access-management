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
public class RememberDeviceFilter extends MfaContextHolder implements Supplier<Boolean> {

    public RememberDeviceFilter(MfaFilterContext context) {
        super(context);
    }

    @Override
    public Boolean get() {
        // If Adaptive MFA is active and KO (rule == false) we bypass this filter
        // Because the device could be known and skip MFA
        if (context.isAmfaActive() && !context.isAmfaRuleTrue()) {
            return false;
        }
        var rememberDeviceSettings = context.getRememberDeviceSettings();
        return !context.isStepUpActive() && rememberDeviceSettings.isActive() && context.deviceAlreadyExists();
    }


}
