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
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_CONDITION_SATISFIED;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_CONDITION_SATISFIED;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_USER_ENROLLING;
import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollStep extends MFAStep {

    private final FactorManager factorManager;

    public MFAEnrollStep(Handler<RoutingContext> wrapper, RuleEngine ruleEngine, FactorManager factorManager) {
        super(wrapper, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);


        var filterContext = new MfaFilterContext(routingContext, client, factorManager);

        final boolean skipMFA = noFactor(client) || userHasFactor(filterContext) || isMfaSkipped(filterContext);
        if (skipMFA) {
            flow.doNext(routingContext);
            return;
        }

        if (isEnrollActive(client) && !userHasFactor(filterContext)) {
            if (isEnrollRequired(client)) {
                routingContext.session().put(MFA_ENROLLMENT_USER_ENROLLING, true);
                flow.exit(this);
                return;
            } else if (isEnrollConditional(client) && enrollConditionNotSatisfied(client, filterContext, routingContext) && !isMfaSkipped(filterContext)) {
                routingContext.session().put(MFA_ENROLLMENT_USER_ENROLLING, true);
                flow.exit(this);
                return;
            } else if (isEnrollOptional(client) && !isMfaSkipped(filterContext)) {
                routingContext.session().put(MFA_ENROLLMENT_USER_ENROLLING, true);
                flow.exit(this);
                return;
            }
            else {
                flow.doNext(routingContext);
                return;
            }

        }

        if (MfaUtils.isChallengeActive(client) && !userHasFactor(filterContext)) {
            if (isChallengeRequired(client)) {
                flow.exit(this);
                return;
            } else if (isChallengeConditional(client) && challengeConditionNotSatisfied(client, filterContext, routingContext)) {
                flow.exit(this);
                return;
            } else if (isChallengeRiskBased(client) && challengeConditionNotSatisfied(client, filterContext, routingContext)) {
                flow.exit(this);
                return;
            }
        }

        flow.doNext(routingContext);
    }


    private boolean isEnrollOptional(Client client) {
        final EnrollSettings settings = MfaUtils.getEnrollSettings(client);
        return MfaEnrollType.OPTIONAL == settings.getType();
    }

    private boolean isEnrollRequired(Client client) {
        final EnrollSettings settings = MfaUtils.getEnrollSettings(client);
        return MfaEnrollType.REQUIRED == settings.getType();
    }

    private boolean isEnrollConditional(Client client) {
        final EnrollSettings settings = MfaUtils.getEnrollSettings(client);
        return MfaEnrollType.CONDITIONAL == settings.getType();
    }

    private boolean enrollConditionNotSatisfied(Client client, MfaFilterContext context, RoutingContext routingContext) {
        final EnrollSettings settings = MfaUtils.getEnrollSettings(client);
        Boolean result = ruleEngine.evaluate(Optional.ofNullable(settings.getEnrollmentRule()).orElse("{}"),
                context.getEvaluableContext(), Boolean.class, false);
        routingContext.session().put(MFA_ENROLLMENT_CONDITION_SATISFIED, result);
        return !result;
    }

    private boolean isRisk(Client client, MfaFilterContext context) {
        if (hasAdaptiveAuthAuthRule(client)) {
            final String adaptiveAuthRule = MfaUtils.getAdaptiveMfaStepUpRule(client);
            return ruleEngine.evaluate(adaptiveAuthRule, context.getEvaluableContext(), Boolean.class, false);
        }

        //todo need to check this
        return false;
    }

    private boolean isChallengeRequired(Client client) {
        ChallengeSettings settings = MfaUtils.getChallengeSettings(client);
        return settings.getType() == MfaChallengeType.REQUIRED;
    }

    private boolean isChallengeConditional(Client client) {
        ChallengeSettings settings = MfaUtils.getChallengeSettings(client);
        return settings.getType() == MfaChallengeType.CONDITIONAL;
    }

    private boolean isChallengeRiskBased(Client client) {
        ChallengeSettings settings = MfaUtils.getChallengeSettings(client);
        return settings.getType() == MfaChallengeType.RISK_BASED;
    }

    private boolean challengeConditionNotSatisfied(Client client, MfaFilterContext context, RoutingContext routingContext) {
        final ChallengeSettings settings = MfaUtils.getChallengeSettings(client);
        Boolean result = ruleEngine.evaluate(Optional.ofNullable(settings.getChallengeRule()).orElse("{}"),
                context.getEvaluableContext(), Boolean.class, false);
        routingContext.session().put(MFA_CHALLENGE_CONDITION_SATISFIED, result);
        return !result;
    }

    private boolean hasAdaptiveAuthAuthRule(Client client) {
        final String adaptiveAuthRule = MfaUtils.getAdaptiveMfaStepUpRule(client);
        return !isNullOrEmpty(adaptiveAuthRule) && !adaptiveAuthRule.isBlank();
    }

    private boolean isEnrollActive(Client client) {
        final MFASettings mfaSettings = Optional.ofNullable(client.getMfaSettings()).orElse(new MFASettings());
        final EnrollSettings enroll = Optional.ofNullable(mfaSettings.getEnroll()).orElse(new EnrollSettings());
        return enroll.isActive();
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

    private boolean userHasFactor(MfaFilterContext context) {
        return context.hasEndUserAlreadyEnrolled() || context.userHasMatchingFactors();
    }

    public boolean isMfaSkipped(MfaFilterContext context) {
        // We need to check whether the AMFA rule is false since we don't know
        final boolean mfaSkipped = context.isMfaSkipped();
        final String mfaStepUpRule = context.getStepUpRule();
        return isNullOrEmpty(mfaStepUpRule) && mfaSkipped;
    }
}
