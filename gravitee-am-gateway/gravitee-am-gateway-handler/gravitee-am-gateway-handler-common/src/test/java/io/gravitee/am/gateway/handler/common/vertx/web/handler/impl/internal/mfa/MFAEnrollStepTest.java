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
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_STOP;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import java.util.Date;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Ashraful HASAN (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class MFAEnrollStepTest {
    private static final String FACTOR_ID = "any-factor-id";

    private static final Handler<RoutingContext> handler = RedirectHandler.create("/mfa/enroll");

    @Mock
    private FactorManager factorManager;

    @Mock
    SpELRuleEngine ruleEngine;

    @Mock
    RoutingContext routingContext;

    @Mock
    AuthenticationFlowChain flow;

    @Mock
    Client client;

    @Mock
    MFASettings mfa;

    @Mock
    EnrollmentSettings enrollmentSettings;

    @Mock
    ChallengeSettings challenge;

    @Mock
    User authUser;

    @Mock
    Factor factor;

    @Mock
    EnrollSettings enroll;

    @Mock
    MfaFilterContext filterContext;

    @Mock
    Session session;

    @Mock
    HttpServerRequest httpServerRequest;

    @Mock
    io.vertx.core.http.HttpServerRequest vertexhttpServerRequest;


    private MFAEnrollStep mfaEnrollStep;

    @BeforeEach
    void setUp() {
        mfaEnrollStep = new MFAEnrollStep(handler, ruleEngine, factorManager);
        when(routingContext.session()).thenReturn(session);
    }

    @Test
    void shouldNotEnrollWhenNoFactor() {
        when(client.getFactors()).thenReturn(Set.of());
        when(routingContext.user()).thenReturn(io.vertx.rxjava3.ext.auth.User.newInstance(authUser));
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);

        mfaEnrollStep.execute(routingContext, flow);

        verifyStop();
    }

    @Test
    void shouldEnrollWhenStepUp() {
        mockContextRequest();
        mockStepUp(true);
        mockAuthUser(true);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(false);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenTypeIsUnknown() {
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(null);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);

        assertThrows(Exception.class, () -> mfaEnrollStep.execute(routingContext, flow));
    }

    @Test
    void shouldEnrollWhenRequiredAndUserNoFactorEnrolled() {
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.REQUIRED);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldEnrollWhenConditionalAndUserNoFactorEnrolled() {
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.CONDITIONAL);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenConditionalRuleSatisfied() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.CONDITIONAL);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);


        mockEnrollmentRuleSatisfied(true);

        mfaEnrollStep.execute(routingContext, flow);

        verifyStop();
    }

    @Test
    void shouldEnrollWhenOptionalAndUserNoFactorEnrolled() {
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.OPTIONAL);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenOptionalUserCanSkipAndSkipped() {
        mockAuthUser(false);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.OPTIONAL);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenOptionalUserCanSkipAndNotSkipped() {
        mockAuthUserWithSkipTime();
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getSkipTimeSeconds()).thenReturn(36000L);
        when(enroll.getType()).thenReturn(MfaEnrollType.OPTIONAL);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);

        mfaEnrollStep.execute(routingContext, flow);

        verifyStop();
    }

    @Test
    void shouldNotEnrollWhenOptionalUserCanNotSkip() {
        mockAuthUser(false);
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.OPTIONAL);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenEnrollAndChallengeDisabled() {
        mockAuthUser(false);
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(false);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);

        mfaEnrollStep.execute(routingContext, flow);

        verifyStop();
    }

    @Test
    void shouldEnrollWhenEnrollDisabledButChallengeEnabled() {
        mockAuthUser(false);
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(true);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenEnrollDisabledButChallengeEnabledAndNotConditionalNotEnrolled() {
        mockAuthUser(false);
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.REQUIRED);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldEnrollWhenEnrollDisabledButChallengeEnabledAndNotConditionalNotEnrolledButHasFactors() {
        mockAuthUserWithEnrolledFactors();
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.REQUIRED);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyContinueWithoutEnrollment();
    }


    @Test
    void shouldNotEnrollWhenEnrollDisabledButChallengeEnabledAndConditionalRuleSatisfied() {
        mockContextRequest();
        mockAuthUser(false);
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.CONDITIONAL);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);

        mockChallengeRuleSatisfied(true);

        mfaEnrollStep.execute(routingContext, flow);

        verifyStop();
    }

    @Test
    void shouldEnrollWhenEnrollDisabledButChallengeEnabledAndConditionalRuleNotSatisfied() {
        mockContextRequest();
        mockAuthUser(false);
        when(enroll.isActive()).thenReturn(false);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.CONDITIONAL);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);


        mockChallengeRuleSatisfied(false);

        mfaEnrollStep.execute(routingContext, flow);

        verifyEnrollment();
    }

    @Test
    void shouldNotEnrollWhenRequiredAndUserHasFactorEnrolled() {
        mockAuthUserWithEnrolledFactors();
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.REQUIRED);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyContinueWithoutEnrollment();
    }

    @Test
    void shouldNotEnrollWhenConditionalAndUserHasFactorEnrolled() {
        mockAuthUserWithEnrolledFactors();
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.CONDITIONAL);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyContinueWithoutEnrollment();
    }

    @Test
    void shouldNotEnrollWhenOptionalAndUserHasFactorEnrolled() {
        mockAuthUserWithEnrolledFactors();
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaEnrollType.OPTIONAL);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verifyContinueWithoutEnrollment();
    }

    private void mockEnrollmentRuleSatisfied(boolean isSatisfied) {
        var rule = "enrollment-rule";
        when(enroll.getEnrollmentRule()).thenReturn(rule);
        when(ruleEngine.evaluate(eq(rule), any(), any(), any())).thenReturn(isSatisfied);
    }

    private void mockChallengeRuleSatisfied(boolean isSatisfied) {
        var rule = "challenge-rule";
        when(challenge.getChallengeRule()).thenReturn(rule);
        when(ruleEngine.evaluate(eq(rule), any(), any(), any())).thenReturn(isSatisfied);
    }

    private void verifyEnrollment() {
        verify(flow, times(1)).exit(mfaEnrollStep);
        verify(flow, times(0)).doNext(routingContext);
    }

    private void verifyContinueWithoutEnrollment() {
        verify(flow, times(1)).doNext(routingContext);
        verify(flow, times(0)).exit(mfaEnrollStep);
    }

    private void verifyStop() {
        verify(session, times(1)).put(MFA_STOP, true);
        verifyContinueWithoutEnrollment();
    }

    private void mockContextRequest() {
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.getDelegate()).thenReturn(vertexhttpServerRequest);
        when(vertexhttpServerRequest.method()).thenReturn(HttpMethod.GET);
        when(session.get(any())).thenReturn(null);
    }

    private void mockAuthUser(boolean strongAuth) {
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        User delegateUser = mock(User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);

        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(strongAuth);
    }

    private void mockAuthUserWithSkipTime() {
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        User delegateUser = mock(User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        when(mockUSer.getMfaEnrollmentSkippedAt()).thenReturn(new Date());
        given(delegateUser.getUser()).willReturn(mockUSer);

        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(false);
    }

    private void mockAuthUserWithEnrolledFactors() {
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        User delegateUser = mock(User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(FACTOR_ID);
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);
        var enrolledFactor2 = new EnrolledFactor();
        enrolledFactor2.setFactorId("2");
        enrolledFactor2.setStatus(FactorStatus.ACTIVATED);
        when(mockUSer.getFactors()).thenReturn(List.of(enrolledFactor, enrolledFactor2));
        given(delegateUser.getUser()).willReturn(mockUSer);

        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(false);
    }

    private void mockStepUp(boolean stepUp) {
        mockContextRequest();
        var rule = "step-up-rule";
        var stepUpSettings = new StepUpAuthenticationSettings();
        stepUpSettings.setActive(true);
        stepUpSettings.setStepUpAuthenticationRule(rule);
        when(mfa.getStepUpAuthentication()).thenReturn(stepUpSettings);
        when(ruleEngine.evaluate(eq(rule), any(), any(), any())).thenReturn(stepUp);
    }
}
