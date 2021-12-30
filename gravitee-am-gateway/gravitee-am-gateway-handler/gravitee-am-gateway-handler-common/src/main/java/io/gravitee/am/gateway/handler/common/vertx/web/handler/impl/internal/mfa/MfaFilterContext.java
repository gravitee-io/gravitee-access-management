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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.ext.web.Session;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaFilterContext {

    private final Client client;
    private final Session session;
    private boolean isAmfaRuleTrue;
    private boolean isStepUpRuleTrue;

    public MfaFilterContext(Client client, Session session) {
        this.client = client;
        this.session = session;
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
}
