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

import io.gravitee.am.common.utils.ConstantKeys;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_COMPLETED;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_STOP;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
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
class MFAChallengeStepTest {
    private static final Handler<RoutingContext> handler = RedirectHandler.create("/mfa/challenge");

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
    EnrollSettings enrollSettings;

    @Mock
    ChallengeSettings challengeSettings;

    @Mock
    User authUser;

    @Mock
    Factor factor;

    @Mock
    ChallengeSettings challenge;

    @Mock
    MfaFilterContext filterContext;

    @Mock
    Session session;

    @Mock
    HttpServerRequest httpServerRequest;

    @Mock
    io.vertx.core.http.HttpServerRequest vertexhttpServerRequest;

    private MFAChallengeStep mfaChallengeStep;

    @BeforeEach
    void setUp() {
        mfaChallengeStep = new MFAChallengeStep(handler, ruleEngine, factorManager);
        when(routingContext.session()).thenReturn(session);
    }
    @Test
    void shouldChallengeWhenStepUp() {
        mockStepUp(true);
        mockAuthUser(true);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldNotChallengeWhenMfaStop() {
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.user()).thenReturn(io.vertx.rxjava3.ext.auth.User.newInstance(authUser));
        when(session.get(MFA_STOP)).thenReturn(true);

        mfaChallengeStep.execute(routingContext, flow);

        verifyContinueWithoutChallenge();
    }

    @Test
    void shouldNotChallengeWhenTypeIsUnknown() {
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(null);
        when(mfa.getChallenge()).thenReturn(challenge);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        assertThrows(Exception.class, () -> mfaChallengeStep.execute(routingContext, flow));
    }

    @Test
    void shouldChallengeWhenRequiredAndInvalidSession() {
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.REQUIRED);
        when(mfa.getChallenge()).thenReturn(challenge);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldChallengeWhenRequiredAndAuthButNoDevice() {
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.REQUIRED);
        when(mfa.getChallenge()).thenReturn(challenge);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldNotChallengeWhenRequiredAndValidAuthAndDevice() {
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.REQUIRED);
        when(mfa.getChallenge()).thenReturn(challenge);

        var rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        when(mfa.getRememberDevice()).thenReturn(rememberDevice);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(true);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        when(session.get(AUTH_COMPLETED)).thenReturn(true);
        when(session.get(DEVICE_ALREADY_EXISTS_KEY)).thenReturn(true);
        when(session.get(MFA_STOP)).thenReturn(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyContinueWithoutChallenge();
    }

    @Test
    void shouldNotChallengeWhenConditionalRuleTrue() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.CONDITIONAL);

        when(routingContext.request()).thenReturn(httpServerRequest);
        when(routingContext.session()).thenReturn(session);
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        mockChallengeRuleSatisfied(true);

        mfaChallengeStep.execute(routingContext, flow);

        verifyContinueWithoutChallenge();
    }

    @Test
    void shouldChallengeWhenConditionalRuleFalse() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.CONDITIONAL);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(MFA_STOP)).thenReturn(false);

        mockChallengeRuleSatisfied(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldChallengeWhenConditionalRuleTrueAndAuth() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.CONDITIONAL);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);

        when(session.get(MFA_STOP)).thenReturn(false);

        mockChallengeRuleSatisfied(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }


    @Test
    void shouldChallengeWhenRiskBasedRuleTrueAndNoAuth() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.RISK_BASED);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.session()).thenReturn(session);
        when(session.get(MFA_STOP)).thenReturn(false);

        mockRiskBasedSatisfied(true);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldChallengeWhenRiskBasedRuleTrueAndAuth() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.RISK_BASED);
        when(routingContext.session()).thenReturn(session);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        mockRiskBasedSatisfied(true);

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }

    @Test
    void shouldNotChallengeWhenRiskBasedRuleFalse() {
        mockContextRequest();
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);
        when(mfa.getChallenge()).thenReturn(challenge);
        when(challenge.isActive()).thenReturn(true);
        when(challenge.getType()).thenReturn(MfaChallengeType.RISK_BASED);
        when(routingContext.session()).thenReturn(session);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(MFA_STOP)).thenReturn(false);

        mockRiskBasedSatisfied(false);

        mfaChallengeStep.execute(routingContext, flow);

        verifyContinueWithoutChallenge();
    }
    @Test
    void shouldChallengeWhenEnrolling() {
        mockAuthUser(false);
        when(client.getMfaSettings()).thenReturn(mfa);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(true);
        when(session.get(MFA_STOP)).thenReturn(false);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(new Object());

        mfaChallengeStep.execute(routingContext, flow);

        verifyChallenge();
    }


    private void verifyChallenge() {
        verify(flow, times(1)).exit(mfaChallengeStep);
        verify(flow, times(0)).doNext(routingContext);
    }

    private void verifyContinueWithoutChallenge() {
        verify(flow, times(1)).doNext(routingContext);
        verify(flow, times(0)).exit(mfaChallengeStep);
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

    private void mockChallengeRuleSatisfied(boolean isSatisfied) {
        var rule = "rule-challenge";
        when(challenge.getChallengeRule()).thenReturn(rule);
        when(ruleEngine.evaluate(eq(rule), any(), any(), any())).thenReturn(isSatisfied);
    }

    private void mockRiskBasedSatisfied(boolean isSatisfied) {
        var rule = "rule-risk-based";
        when(mfa.getAdaptiveAuthenticationRule()).thenReturn(rule);
        when(ruleEngine.evaluate(eq(rule), any(), any(), any())).thenReturn(isSatisfied);
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
