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

import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaFilterContext {

    private final RoutingContext routingContext;
    private final Client client;
    private final Session session;
    private final User endUser;
    private boolean isAmfaRuleTrue;
    private boolean isStepUpRuleTrue;

    public MfaFilterContext(RoutingContext routingContext, Client client) {
        this.routingContext = routingContext;
        this.client = client;
        this.session = routingContext.session();
        this.endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
    }

    public String getAmfaRule() {
        return MfaUtils.getAdaptiveMfaStepUpRule(client);
    }

    public boolean isAmfaActive() {
        final String amfaRule = getAmfaRule();
        return !isNullOrEmpty(amfaRule) && !amfaRule.isBlank();
    }

    public boolean isAmfaRuleTrue() {
        return isAmfaRuleTrue;
    }

    public void setAmfaRuleTrue(boolean amfaRuleTrue) {
        isAmfaRuleTrue = amfaRuleTrue;
    }

    public String getStepUpRule() {
        return MfaUtils.getMfaStepUpRule(client);
    }

    public boolean isStepUpRuleTrue() {
        return isStepUpRuleTrue;
    }

    public void setStepUpRuleTrue(boolean stepUpRuleTrue) {
        isStepUpRuleTrue = stepUpRuleTrue;
    }

    public boolean isStepUpActive() {
        final String stepUpRule = getStepUpRule();
        return !isNullOrEmpty(stepUpRule) && !stepUpRule.isBlank();
    }

    public boolean isMfaSkipped() {
        return MfaUtils.isMfaSkipped(session);
    }

    public boolean isUserStronglyAuth() {
        return MfaUtils.isUserStronglyAuth(session);
    }

    public RememberDeviceSettings getRememberDeviceSettings() {
        return MfaUtils.getRememberDeviceSettings(client);
    }

    public boolean deviceAlreadyExists() {
        return MfaUtils.deviceAlreadyExists(session);
    }

    public Object getLoginAttempt() {
        return session.get(LOGIN_ATTEMPT_KEY);
    }

    public boolean hasEndUserAlreadyEnrolled() {
        return nonNull(session.get(ENROLLED_FACTOR_ID_KEY));
    }

    public boolean hasUserChosenAlternativeFactor() {
        return nonNull(session.get(ALTERNATIVE_FACTOR_ID_KEY));
    }

    public Map<String, Object> getEvaluableContext() {
        final Map<String, Object> data = getEvaluableAttributes(routingContext);
        final Object loginAttempt = this.getLoginAttempt();
        data.put(LOGIN_ATTEMPT_KEY, isNull(loginAttempt) ? 0 : loginAttempt);
        return Map.of(
                "request", new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())),
                "context", new EvaluableExecutionContext(data)
        );
    }

    public boolean userHasMatchingFactors() {
        if (isNull(endUser.getFactors()) || endUser.getFactors().isEmpty()) {
            return false;
        }
        return endUser.getFactors().stream()
                .map(EnrolledFactor::getFactorId)
                .anyMatch(client.getFactors()::contains);
    }

    public boolean userHasMatchingActivatedFactors() {
        if (isNull(endUser.getFactors()) || endUser.getFactors().isEmpty()) {
            return false;
        }
        return endUser.getFactors().stream()
                .filter(factor -> ACTIVATED.equals(factor.getStatus()))
                .map(EnrolledFactor::getFactorId)
                .anyMatch(client.getFactors()::contains);
    }
}
