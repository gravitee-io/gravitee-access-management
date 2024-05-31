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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_STOP;
import static io.gravitee.am.model.MfaEnrollType.CONDITIONAL;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MfaUtils {

    public static boolean isUserStronglyAuth(Session session) {
        return TRUE.equals(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY));
    }

    public static StepUpAuthenticationSettings getMfaStepUp(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getStepUpAuthentication)
                .orElse(new StepUpAuthenticationSettings());
    }

    public static String getAdaptiveMfaStepUpRule(Client client) {
        return ofNullable(client.getMfaSettings()).map(MFASettings::getChallenge).map(ChallengeSettings::getChallengeRule).orElse("");
    }

    public static RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    public static boolean deviceAlreadyExists(Session session) {
        return TRUE.equals(session.get(DEVICE_ALREADY_EXISTS_KEY));
    }

    public static ChallengeSettings getChallengeSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getChallenge)
                .orElse(new ChallengeSettings());
    }

    public static EnrollSettings getEnrollSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getEnroll)
                .orElse(new EnrollSettings());
    }

    public static MFASettings getMfaSettings(Client client) {
        return ofNullable(client.getMfaSettings()).orElse(new MFASettings());
    }

    public static boolean isChallengeActive(Client client) {
        return getChallengeSettings(client).isActive();
    }

    public static void stopMfaFlow(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_STOP, true);
        flow.doNext(routingContext.routingContext());
    }

    public static void executeFlowStep(MfaFilterContext routingContext, AuthenticationFlowChain flow, MFAStep mFAStep) {
        routingContext.session().put(MFA_STOP, false);
        flow.exit(mFAStep);
    }

    public static void continueMfaFlow(MfaFilterContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_STOP, false);
        flow.doNext(routingContext.routingContext());
    }

    public static boolean isMfaFlowStopped(MfaFilterContext context) {
        return ofNullable((Boolean) context.session().get(MFA_STOP)).orElse(false);
    }

    public static boolean hasFactors(Client client, FactorManager factorManager) {
        var factors = ofNullable(client.getFactorSettings())
                .map(FactorSettings::getApplicationFactors)
                .orElse(List.of())
                .stream()
                .map(ApplicationFactorSettings::getId)
                .collect(Collectors.toSet());
        return !factors.isEmpty() && notOnlyRecoveryCodeFactors(factors, factorManager);
    }

    private static boolean notOnlyRecoveryCodeFactors(Set<String> factorIds, FactorManager factorManager) {
        return factorIds.stream().anyMatch(factorId ->
                !factorManager.getFactor(factorId).getFactorType().equals(FactorType.RECOVERY_CODE));
    }

    public static boolean evaluateRule(String rule, MfaFilterContext context, RuleEngine ruleEngine) {
        return !isNullOrEmpty(rule) && !rule.isBlank() && ruleEngine.evaluate(rule, context.getEvaluableContext(), Boolean.class, false);
    }

    public static boolean challengeConditionSatisfied(Client client, MfaFilterContext context, RuleEngine ruleEngine) {
        return evaluateRule(getChallengeSettings(client).getChallengeRule(), context, ruleEngine);
    }

    public static boolean stepUpRequired(MfaFilterContext context, Client client, RuleEngine ruleEngine) {
        var stepUpSettings = getMfaStepUp(client);
        return stepUpSettings.isActive() && evaluateRule(stepUpSettings.getStepUpAuthenticationRule(), context, ruleEngine);
    }

    public static boolean isCanSkip(RoutingContext routingContext, Client client) {
        var enrollSettings = MfaUtils.getEnrollSettings(client);
        boolean enrollmentActive = enrollSettings.isActive();
        boolean isNotForcedEnrollment = enrollSettings.getForceEnrollment() != null && !enrollSettings.getForceEnrollment();
        boolean isConditionalAndSkipped = CONDITIONAL.equals(enrollSettings.getType()) && TRUE.equals(routingContext.session().get(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY));
        return enrollmentActive && (isNotForcedEnrollment || isConditionalAndSkipped);
    }
}
