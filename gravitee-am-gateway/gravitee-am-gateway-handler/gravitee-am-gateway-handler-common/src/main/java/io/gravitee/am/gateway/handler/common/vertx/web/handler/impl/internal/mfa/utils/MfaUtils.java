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

import static com.google.common.base.Strings.isNullOrEmpty;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_STOP;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import static java.lang.Boolean.TRUE;
import java.util.Objects;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import java.util.Set;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
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
        return ofNullable(client.getMfaSettings()).orElse(new MFASettings()).getAdaptiveAuthenticationRule();
    }

    public static RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    public static boolean deviceAlreadyExists(Session session) {
        return TRUE.equals(session.get(DEVICE_ALREADY_EXISTS_KEY));
    }

    public static ChallengeSettings getChallengeSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .filter(Objects::nonNull)
                .map(MFASettings::getChallenge)
                .orElse(new ChallengeSettings());
    }

    public static EnrollSettings getEnrollSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .filter(Objects::nonNull)
                .map(MFASettings::getEnroll)
                .orElse(new EnrollSettings());
    }

    public static MFASettings getMfaSettings(Client client) {
        return ofNullable(client.getMfaSettings()).orElse(new MFASettings());
    }

    public static boolean isChallengeActive(Client client) {
        return of(getChallengeSettings(client).isActive()).orElse(false);
    }

    public static void stop(RoutingContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_STOP, true);
        flow.doNext(routingContext);
    }

    public static void redirect(RoutingContext routingContext, AuthenticationFlowChain flow, MFAStep mFAStep) {
        routingContext.session().put(MFA_STOP, false);
        flow.exit(mFAStep);
    }

    public static void continueFlow(RoutingContext routingContext, AuthenticationFlowChain flow) {
        routingContext.session().put(MFA_STOP, false);
        flow.doNext(routingContext);
    }

    public static boolean isMfaStop(RoutingContext context) {
        return ofNullable((Boolean) context.session().get(MFA_STOP)).orElse(false);
    }

    public static boolean hasFactor(Client client, FactorManager factorManager) {
        final Set<String> factors = client.getFactors();
        return nonNull(factors) && !factors.isEmpty() && !onlyRecoveryCodeFactor(factors, factorManager);
    }

    private static boolean onlyRecoveryCodeFactor(Set<String> factors, FactorManager factorManager) {
        return factors.size() == 1 && factors.stream().findFirst().map(factorId ->
                factorManager.getFactor(factorId).getFactorType().equals(FactorType.RECOVERY_CODE)
        ).orElse(false);
    }

    public static boolean evaluateRule(String rule, MfaFilterContext context, RuleEngine ruleEngine) {
        return !isNullOrEmpty(rule) && !rule.isBlank() && ruleEngine.evaluate(rule, context.getEvaluableContext(), Boolean.class, false);
    }

    public static boolean challengeConditionSatisfied(Client client, MfaFilterContext context, RuleEngine ruleEngine) {
        return evaluateRule(getChallengeSettings(client).getChallengeRule(), context, ruleEngine);
    }

    public static boolean stepUp(MfaFilterContext context, Client client, RuleEngine ruleEngine) {
        var stepUpSettings = getMfaStepUp(client);
        return context.isUserStronglyAuth()
                && stepUpSettings.getActive()
                && evaluateRule(stepUpSettings.getStepUpAuthenticationRule(), context, ruleEngine);
    }
}
