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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.filter;

import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.model.RememberDeviceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class MfaChallengeFilter_RememberDevice_Test {

    @Mock
    private MfaFilterContext mfaFilterContext;
    @Mock
    private RuleEngine ruleEngine;

    private RememberDeviceSettings rememberDeviceSettings = new RememberDeviceSettings();

    @Nested
    public class WithoutRememberDevice {

        @BeforeEach
        public void initSettings() {
            rememberDeviceSettings.setSkipRememberDevice(false);
            rememberDeviceSettings.setActive(false);
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_true_and_remember_device_disabled() {
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);
            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }
    }

    @Nested
    public class WithoutSkipRememberDevice {

        @BeforeEach
        public void initSettings() {
            rememberDeviceSettings.setSkipRememberDevice(false);
            rememberDeviceSettings.setActive(true);
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_false_and_remember_device_enabled() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_true_and_remember_device_enabled_with_unknown_device() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_true_and_remember_device_enabled_with_known_device() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_false_even_if_user_strongly_authenticated_and_stepup_active() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.isStepUpActive()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_false_even_if_user_strongly_authenticated_and_stepup_inactive() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.isStepUpActive()).thenReturn(false);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_true_and_user_strongly_authenticated() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }
    }

    @Nested
    public class WithSkipRememberDevice {

        @BeforeEach
        public void initSettings() {
            rememberDeviceSettings.setSkipRememberDevice(true);
            rememberDeviceSettings.setActive(true);
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_false_and_device_is_unknown() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_false_and_user_strongly_authenticate_and_stepup_inactive() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.isStepUpActive()).thenReturn(false);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_UNSAFE_when_adaptiveMFA_return_false_and_user_strongly_authenticate_and_stepup_active() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.isStepUpActive()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertFalse(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_true_and_user_strongly_authenticate() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isUserStronglyAuth()).thenReturn(true);
            lenient().when(mfaFilterContext.isUserFullyAuthenticated()).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_false_and_device_is_known() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(false);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_true_and_device_is_unknown() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(false);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }

        @Test
        public void should_return_SAFE_when_adaptiveMFA_return_true_and_device_is_known() {
            lenient().when(ruleEngine.evaluate(any(), anyMap(), any(), eq(false))).thenReturn(true);
            lenient().when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
            lenient().when(mfaFilterContext.isAmfaActive()).thenReturn(true);
            lenient().when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
            lenient().when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
            lenient().when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
            lenient().when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

            assertTrue(new MfaChallengeFilter(mfaFilterContext, ruleEngine).get());
        }
    }
}
