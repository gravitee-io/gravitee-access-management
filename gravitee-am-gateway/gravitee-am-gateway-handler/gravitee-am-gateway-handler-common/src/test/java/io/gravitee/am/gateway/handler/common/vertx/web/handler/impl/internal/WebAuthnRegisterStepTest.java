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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthnRegisterStepTest {

    private static final String OTP_FACTOR_ID = "otp-factor";
    private static final String RECOVERY_FACTOR_ID = "recovery-factor";

    @Mock
    private FactorManager factorManager;
    @Mock
    private CredentialGatewayService credentialService;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private Session session;
    @Mock
    private AuthenticationFlowChain flow;

    private WebAuthnRegisterStep step;

    @BeforeEach
    void setUp() {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setPasswordlessEnabled(true);
        Domain domain = new Domain();
        domain.setLoginSettings(loginSettings);
        step = new WebAuthnRegisterStep(domain, RedirectHandler.create("/webauthn/register"), factorManager, credentialService);

        when(routingContext.session()).thenReturn(session);
        when(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(new Client());
        lenient().when(factorManager.getFactor(OTP_FACTOR_ID)).thenReturn(factor(FactorType.OTP));
        lenient().when(factorManager.getFactor(RECOVERY_FACTOR_ID)).thenReturn(factor(FactorType.RECOVERY_CODE));
    }

    @Test
    void shouldDeferRegistration_whenUserHasActivatedFactorAndIsNotStronglyAuthenticated() {
        givenAuthenticatedUser(userWithActivatedFactor(OTP_FACTOR_ID));

        step.execute(routingContext, flow);

        verify(flow).doNext(routingContext);
        verify(flow, never()).exit(any());
    }

    @Test
    void shouldPromptRegistration_whenUserHasActivatedFactorAndIsStronglyAuthenticated() {
        givenAuthenticatedUser(userWithActivatedFactor(OTP_FACTOR_ID));
        lenient().when(session.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY)).thenReturn(true);

        step.execute(routingContext, flow);

        verify(flow).exit(step);
    }

    @Test
    void shouldPromptRegistration_whenUserHasNoFactor() {
        givenAuthenticatedUser(new User());

        step.execute(routingContext, flow);

        verify(flow).exit(step);
    }

    @Test
    void shouldPromptRegistration_whenUserOnlyHasRecoveryCodes() {
        givenAuthenticatedUser(userWithActivatedFactor(RECOVERY_FACTOR_ID));

        step.execute(routingContext, flow);

        verify(flow).exit(step);
    }

    private void givenAuthenticatedUser(User user) {
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User authUser = new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user);
        lenient().when(routingContext.user()).thenReturn(io.vertx.rxjava3.ext.auth.User.newInstance(authUser));
    }

    private static Factor factor(FactorType type) {
        Factor factor = new Factor();
        factor.setFactorType(type);
        return factor;
    }

    private static User userWithActivatedFactor(String factorId) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);
        User user = new User();
        user.setFactors(List.of(enrolledFactor));
        return user;
    }
}
