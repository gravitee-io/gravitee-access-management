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
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful HASAN (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class MFAEnrollStepTest {
    private static final String FACTOR_ID = "any-factor-id";

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
    MFASettings mfaSettings;

    @Mock
    EnrollmentSettings enrollmentSettings;

    @Mock
    ChallengeSettings challengeSettings;

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


    private MFAEnrollStep mfaEnrollStep;

    @BeforeEach
    void setUp() {
        Handler<RoutingContext> handler = RedirectHandler.create("/mfa/enroll");
        mfaEnrollStep = new MFAEnrollStep(handler,ruleEngine, factorManager);
    }

    @Test
    @DisplayName("Should not enroll, client has no factor")
    void should_not_enroll_clint_noFactor() {
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.user()).thenReturn(io.vertx.rxjava3.ext.auth.User.newInstance(authUser));
        when(client.getFactors()).thenReturn(Set.of());

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).doNext(routingContext);
        verify(flow,times(0)).exit(mfaEnrollStep);
    }

    @Test
    @DisplayName("Enroll MFA when enroll enabled and required, user has no factor")
    void should_enroll_enrollActive_required_noFactor() {
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaType.REQUIRED);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }

    @Test
    @DisplayName("Enroll MFA when enroll enabled and conditional, user has no factor")
    void should_enroll_enrollActive_conditional_noFactor() {
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaType.CONDITIONAL);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        when(ruleEngine.evaluate(anyString(), anyMap(), any(), any())).thenReturn(false);
        //when(routingContext.data()).thenReturn(Map.of());
        io.vertx.core.http.HttpServerRequest mockHttpServerReq = mock(io.vertx.core.http.HttpServerRequest.class);
        when(mockHttpServerReq.method()).thenReturn(HttpMethod.GET);
        when(routingContext.request()).thenReturn(HttpServerRequest.newInstance(mockHttpServerReq));

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }

    @Test
    @DisplayName("Enroll MFA when enroll enabled and optional, user has no factor")
    void should_enroll_enrollActive_optional_noFactor() {
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(enroll.isActive()).thenReturn(true);
        when(enroll.getType()).thenReturn(MfaType.OPTIONAL);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }

    @Test
    @DisplayName("Enroll disabled, challenge enabled and required, user has no factor ")
    void should_enroll_MFA_clientHasFactors_enrollDisabled_challengeRequired() {
        when(enroll.isActive()).thenReturn(false);
        when(challengeSettings.isActive()).thenReturn(true);
        when(challengeSettings.getType()).thenReturn(MfaType.REQUIRED);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getChallenge()).thenReturn(challengeSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }

    @Test
    @DisplayName("Enroll disabled, challenge enabled and conditional, user has no factor ")
    void should_enroll_noFactor_challengeConditional() {
        when(enroll.isActive()).thenReturn(false);
        when(challengeSettings.isActive()).thenReturn(true);
        when(challengeSettings.getType()).thenReturn(MfaType.CONDITIONAL);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getChallenge()).thenReturn(challengeSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);

        when(ruleEngine.evaluate(anyString(), anyMap(), any(), any())).thenReturn(false);
        //when(routingContext.data()).thenReturn(Map.of());
        io.vertx.core.http.HttpServerRequest mockHttpServerReq = mock(io.vertx.core.http.HttpServerRequest.class);
        when(mockHttpServerReq.method()).thenReturn(HttpMethod.GET);
        when(routingContext.request()).thenReturn(HttpServerRequest.newInstance(mockHttpServerReq));

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }

    @Test
    @DisplayName("Enroll disabled, challenge enabled and risk based, user has no factor ")
    void should_enroll_noFactor_challengeRiskBased() {
        when(enroll.isActive()).thenReturn(false);
        when(challengeSettings.isActive()).thenReturn(true);
        when(challengeSettings.getType()).thenReturn(MfaType.RISK_BASED);

        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(client.getFactors()).thenReturn(Set.of(FACTOR_ID));
        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(mfaSettings.getChallenge()).thenReturn(challengeSettings);
        when(mfaSettings.getEnroll()).thenReturn(enroll);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(routingContext.session()).thenReturn(session);
        when(session.get(ENROLLED_FACTOR_ID_KEY)).thenReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        when(routingContext.user()).thenReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        io.gravitee.am.model.User mockUSer = mock(io.gravitee.am.model.User.class);
        given(delegateUser.getUser()).willReturn(mockUSer);

        when(ruleEngine.evaluate(anyString(), anyMap(), any(), any())).thenReturn(false);
        //when(routingContext.data()).thenReturn(Map.of());
        io.vertx.core.http.HttpServerRequest mockHttpServerReq = mock(io.vertx.core.http.HttpServerRequest.class);
        when(mockHttpServerReq.method()).thenReturn(HttpMethod.GET);
        when(routingContext.request()).thenReturn(HttpServerRequest.newInstance(mockHttpServerReq));

        mfaEnrollStep.execute(routingContext, flow);

        verify(flow,times(1)).exit(mfaEnrollStep);
        verify(flow,times(0)).doNext(routingContext);
    }
}
