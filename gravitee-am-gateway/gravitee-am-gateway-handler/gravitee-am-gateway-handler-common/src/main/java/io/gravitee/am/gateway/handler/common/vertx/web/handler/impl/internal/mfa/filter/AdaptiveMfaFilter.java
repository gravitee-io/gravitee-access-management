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
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.vertx.reactivex.core.http.HttpServerRequest;

import java.util.Map;
import java.util.function.Supplier;

import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static java.util.Objects.isNull;


/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AdaptiveMfaFilter extends MfaContextHolder implements Supplier<Boolean> {

    private final RuleEngine ruleEngine;
    private final Map<String, Object> data;
    private final HttpServerRequest request;

    private Boolean isRuleTrue = null;

    public AdaptiveMfaFilter(
            MfaFilterContext context,
            RuleEngine ruleEngine,
            HttpServerRequest request,
            Map<String, Object> data) {
        super(context);
        this.ruleEngine = ruleEngine;
        this.data = data;
        this.request = request;
    }

    @Override
    public Boolean get() {
        if (isNull(isRuleTrue)) {
            if (!context.isAmfaActive()) {
                context.setAmfaRuleTrue(false);
                return false;
            }

            final Object loginAttempt = context.getLoginAttempt();
            data.put(LOGIN_ATTEMPT_KEY, isNull(loginAttempt) ? 0 : loginAttempt);
            var parameters = Map.of(
                    "request", new EvaluableRequest(new VertxHttpServerRequest(request.getDelegate())),
                    "context", new EvaluableExecutionContext(data)
            );
            // We are retaining the value since other features will use it in the chain
            context.setAmfaRuleTrue(ruleEngine.evaluate(context.getAmfaRule(), parameters, Boolean.class, false));
        }
        // If one of the other filter chains are active. We want to make sure that
        // if Adaptive MFA skips (rule == true) we want other MFA methods to trigger
        var rememberDevice = context.getRememberDeviceSettings();
        return !rememberDevice.isActive() && !context.isStepUpActive() && context.isAmfaRuleTrue();
    }
}
