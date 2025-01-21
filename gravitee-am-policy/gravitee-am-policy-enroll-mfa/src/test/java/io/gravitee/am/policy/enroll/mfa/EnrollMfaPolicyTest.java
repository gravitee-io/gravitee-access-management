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
package io.gravitee.am.policy.enroll.mfa;

import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.policy.enroll.mfa.configuration.EnrollMfaPolicyConfiguration;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrollMfaPolicyTest {

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private EnrollMfaPolicyConfiguration configuration;

    @Mock
    private TemplateEngine templateEngine;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldContinue_noFactorId() throws Exception {
        when(configuration.getFactorId()).thenReturn(null);
        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
    }

    @Test
    public void shouldContinue_noFactor() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        FactorManager factorManager = mock(FactorManager.class);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.empty());
        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
    }

    @Test
    public void shouldContinue_noUser() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        FactorManager factorManager = mock(FactorManager.class);
        when(factorManager.getFactor("factor-id")).thenReturn(new Factor());
        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(null);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
    }

    @Test
    public void shouldContinue_alreadyEnrolled() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        FactorManager factorManager = mock(FactorManager.class);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.of(new Factor()));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(false);
        when(factorManager.get(eq("factor-id"))).thenReturn(factorProvider);
        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);
        User user = mock(User.class);
        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        when(enrolledFactor.getFactorId()).thenReturn("factor-id");
        when(user.getFactors()).thenReturn(Collections.singletonList(enrolledFactor));
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);

        UserGatewayService userService = mock(UserGatewayService.class);
        when(userService.updateFactor(anyString(), any(), any())).thenReturn(Single.just(new User()));
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
        verify(userService, never()).addFactor(anyString(), any(), any());
        verify(userService, never()).updateFactor(anyString(), any(), any());
    }

    @Test
    public void shouldUpdate_alreadyEnrolled() throws Exception {
        final var newEmail = "new-user@email.com";
        final var oldEmail = "old-user@email.com";
        final var factorId = "factor-id";
        when(configuration.getFactorId()).thenReturn(factorId);
        when(configuration.getValue()).thenReturn(newEmail);
        when(configuration.isRefresh()).thenReturn(true);

        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.getClientFactor(any(), eq(factorId))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(false);
        when(factorManager.get(eq(factorId))).thenReturn(factorProvider);

        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, oldEmail));

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(user.getFactors()).thenReturn(List.of(enrolledFactor));
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getValue(configuration.getValue(), String.class)).thenReturn(newEmail);

        UserGatewayService userService = mock(UserGatewayService.class);
        when(userService.updateFactor(anyString(), any(), any())).thenReturn(Single.just(new User()));
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
        verify(userService).updateFactor(anyString(), argThat(updatedEnrolledFact -> updatedEnrolledFact.getChannel().getTarget().equals(newEmail)), any());
        verify(userService, never()).addFactor(anyString(), any(), any());
    }

    @Test
    public void shouldContinue_alreadyEnrolled_NoChanges() throws Exception {
        final var email = "user@email.com";
        final var factorId = "factor-id";
        when(configuration.getFactorId()).thenReturn(factorId);
        when(configuration.getValue()).thenReturn(email);
        when(configuration.isRefresh()).thenReturn(true);

        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.getClientFactor(any(), eq(factorId))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(false);
        when(factorManager.get(eq(factorId))).thenReturn(factorProvider);

        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, email));

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(user.getFactors()).thenReturn(List.of(enrolledFactor));
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getValue(configuration.getValue(), String.class)).thenReturn(email);

        UserGatewayService userService = mock(UserGatewayService.class);
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
        verify(userService, never()).updateFactor(anyString(), any(), any());
        verify(userService, never()).addFactor(anyString(), any(), any());
    }

    @Test
    public void shouldFail_missingValue() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        when(configuration.getValue()).thenReturn(null);
        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(false);
        when(factorManager.get(eq("factor-id"))).thenReturn(factorProvider);
        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);
        User user = mock(User.class);
        when(user.getFactors()).thenReturn(Collections.emptyList());
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1))
                .failWith(argThat(result -> EnrollMfaPolicy.GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY.equals(result.key())));
    }

    @Test
    public void shouldContinue_missingValueForHTTP() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        when(configuration.getValue()).thenReturn(null);

        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.HTTP);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(false);
        when(factorManager.get(eq("factor-id"))).thenReturn(factorProvider);

        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(user.getFactors()).thenReturn(new ArrayList<>());
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getValue(configuration.getValue(), String.class)).thenReturn(null);

        UserGatewayService userService = mock(UserGatewayService.class);
        when(userService.addFactor(anyString(), any(), any())).thenReturn(Single.just(new User()));
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
    }

    @Test
    public void shouldContinue_missingValueForOTP() throws Exception {
        when(configuration.getFactorId()).thenReturn("factor-id");
        when(configuration.getValue()).thenReturn(null);

        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.OTP);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(true);
        when(factorManager.get(eq("factor-id"))).thenReturn(factorProvider);

        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(user.getUsername()).thenReturn("username");
        when(user.getFactors()).thenReturn(new ArrayList<>());
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getValue(configuration.getValue(), String.class)).thenReturn(null);

        UserGatewayService userService = mock(UserGatewayService.class);
        when(userService.addFactor(anyString(), any(), any())).thenReturn(Single.just(new User()));
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
        verify(userService).addFactor(anyString(), argThat(enrolledFactor -> {
            final EnrolledFactorSecurity security = enrolledFactor.getSecurity();
            return security != null && security.getAdditionalData() != null && security.getAdditionalData().containsKey(FactorDataKeys.KEY_MOVING_FACTOR);
        }), any());
    }

    @Test
    public void shouldContinue_nominalCase_SMS_NoVariableFactorSecurity() throws Exception {
        shouldContinue_nominalCase_SMS(false);
    }

    @Test
    public void shouldContinue_nominalCase_SMS_WithVariableFactorSecurity() throws Exception {
        shouldContinue_nominalCase_SMS(true);
    }

    private void shouldContinue_nominalCase_SMS(boolean useVariableFactorSecurity) throws InterruptedException {
        when(configuration.getFactorId()).thenReturn("factor-id");
        when(configuration.getValue()).thenReturn("0102030405");

        FactorManager factorManager = mock(FactorManager.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.getClientFactor(any(), eq("factor-id"))).thenReturn(Optional.of(factor));
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.useVariableFactorSecurity()).thenReturn(useVariableFactorSecurity);
        when(factorManager.get(eq("factor-id"))).thenReturn(factorProvider);

        when(executionContext.getComponent(FactorManager.class)).thenReturn(factorManager);

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(user.getFactors()).thenReturn(null);
        when(executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).thenReturn(user);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getValue(configuration.getValue(), String.class)).thenReturn("0102030405");

        UserGatewayService userService = mock(UserGatewayService.class);
        when(userService.addFactor(anyString(), any(), any())).thenReturn(Single.just(new User()));
        when(executionContext.getComponent(UserGatewayService.class)).thenReturn(userService);

        executePolicy(configuration, request, response, executionContext, policyChain);
        verify(policyChain, times(1)).doNext(request, response);
        verify(user).setFactors(any());
        if (useVariableFactorSecurity) {
            verify(user).setFactors(argThat(enrolledFactors -> enrolledFactors.get(0).getSecurity() != null
                    && FactorSecurityType.SHARED_SECRET.equals(enrolledFactors.get(0).getSecurity().getType())
                    && enrolledFactors.get(0).getSecurity().getAdditionalData().containsKey(FactorDataKeys.KEY_MOVING_FACTOR)));
        } else {
            verify(user).setFactors(argThat(enrolledFactors -> enrolledFactors.get(0).getSecurity() == null));
        }
    }

    private void executePolicy(
            EnrollMfaPolicyConfiguration configuration,
            Request request,
            Response response,
            ExecutionContext executionContext,
            PolicyChain policyChain
    ) throws InterruptedException {
        final CountDownLatch lock = new CountDownLatch(1);

        new EnrollMfaPolicy(configuration).onRequest(request, response, executionContext, policyChain);

        lock.await(50, TimeUnit.MILLISECONDS);
    }

}
