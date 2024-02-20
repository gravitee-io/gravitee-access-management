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

import static io.gravitee.am.common.factor.FactorType.RECOVERY_CODE;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_ENROLLMENT_SKIP_TIME_SECONDS;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import static io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils.evaluateRule;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import static java.lang.Boolean.TRUE;
import java.util.Date;
import java.util.List;
import java.util.Map;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import static java.util.Optional.ofNullable;
import java.util.stream.Collectors;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaFilterContext {

    private final RoutingContext routingContext;
    private final Client client;
    private final Session session;
    private final User endUser;
    private final FactorManager factorManager;
    private final RuleEngine ruleEngine;

    public MfaFilterContext(RoutingContext routingContext, Client client, FactorManager factorManager, RuleEngine ruleEngine) {
        this.routingContext = routingContext;
        this.client = client;
        this.session = routingContext.session();
        this.endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        this.factorManager = factorManager;
        this.ruleEngine = ruleEngine;
    }

    public boolean isEnrollSkipped() {
        final boolean canSkip = MfaUtils.isCanSkip(client, routingContext);
        if (canSkip && nonNull(endUser.getMfaEnrollmentSkippedAt())) {
            Date now = new Date();
            long skipTime = ofNullable(MfaUtils.getEnrollSettings(client).getSkipTimeSeconds()).orElse(DEFAULT_ENROLLMENT_SKIP_TIME_SECONDS) * 1000L;
            return endUser.getMfaEnrollmentSkippedAt().getTime() + skipTime > now.getTime();
        }
        return false;
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

    public boolean isEndUserEnrolling() {
        return nonNull(session.get(ENROLLED_FACTOR_ID_KEY));
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

    public boolean userHasMatchingActivatedFactors() {
        if (isNull(endUser.getFactors()) || endUser.getFactors().isEmpty()) {
            return false;
        }
        var applicationFactors = client.getFactorSettings().getApplicationFactors().stream().map(ApplicationFactorSettings::getId).collect(Collectors.toSet());
        return endUser.getFactors().stream()
                .filter(factor -> ACTIVATED.equals(factor.getStatus()))
                .map(EnrolledFactor::getFactorId)
                .filter(this::isNotRecoveryCodeType)
                .anyMatch(applicationFactors::contains);
    }

    public boolean isDeviceRiskAssessmentEnabled() {
        return ofNullable(client.getRiskAssessment())
                .filter(RiskAssessmentSettings::isEnabled)
                .map(RiskAssessmentSettings::getDeviceAssessment)
                .map(AssessmentSettings::isEnabled)
                .orElse(false);
    }

    public boolean isChallengeOnceCompleted() {
        return TRUE.equals(session.get(MFA_CHALLENGE_COMPLETED_KEY));
    }


    public void setDefaultFactorWhenApplied() {
        String enrollingFactorId = session.get(ENROLLED_FACTOR_ID_KEY);
        FactorSettings factorSettings = client.getFactorSettings();
        if (nonNull(enrollingFactorId) && nonNull(factorSettings) && !matchFactorRule(enrollingFactorId, factorSettings)) {
            session.put(ENROLLED_FACTOR_ID_KEY, getDefaultFactorId());
        }
    }

    public boolean matchFactorRule(String factorId, FactorSettings factorSettings) {
        var appFactors = factorSettings.getApplicationFactors();
        if (appFactors.size() == 1 && factorId.equals(factorSettings.getDefaultFactorId())) {
            return true;
        } else {
            var appFactor = appFactors.stream().filter(f -> f.getId().equals(factorId)).findFirst();
            return appFactor.isPresent() && isMatchFactorRule(appFactor.get().getSelectionRule());
        }
    }

    private boolean isMatchFactorRule(String selectionRule) {
        return !hasText(selectionRule) || TRUE.equals(evaluateRule(selectionRule, this, ruleEngine));
    }

    private String getDefaultFactorId() {
        return client.getFactorSettings().getDefaultFactorId();
    }

    private boolean isNotRecoveryCodeType(String factorId) {
        var factor = factorManager.getFactor(factorId);
        return nonNull(factor) && !RECOVERY_CODE.equals(factor.getFactorType());
    }
}
