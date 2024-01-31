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

import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RememberDevideFilterTest {

    @Mock
    private RoutingContext routingContext;

    @Mock
    private Client client;

    @Mock
    private FactorManager factorManager;

    @Mock
    private MfaFilterContext mfaFilterContext;

    @Test
    public void should_return_true_when_adaptiveMFA_return_true_and_skipRememberDevice_disable() {
        final var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setSkipRememberDevice(false);
        rememberDeviceSettings.setActive(true);
        when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
        when(mfaFilterContext.isAmfaActive()).thenReturn(true);
        when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
        when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

        assertTrue(new RememberDeviceFilter(mfaFilterContext).get());
    }

    @Test
    public void should_return_true_when_adaptiveMFA_return_true_and_skipRememberDevice_enabled_and_unknown_device() {
        final var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setSkipRememberDevice(true);
        rememberDeviceSettings.setActive(true);
        when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
        when(mfaFilterContext.isAmfaActive()).thenReturn(true);
        when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(true);
        when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

        assertTrue(new RememberDeviceFilter(mfaFilterContext).get());
    }

    @Test
    public void should_return_true_when_adaptiveMFA_return_false_and_device_known() {
        final var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setSkipRememberDevice(true);
        rememberDeviceSettings.setActive(true);
        when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
        when(mfaFilterContext.isAmfaActive()).thenReturn(true);
        when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
        when(mfaFilterContext.deviceAlreadyExists()).thenReturn(true);
        when(mfaFilterContext.userHasMatchingActivatedFactors()).thenReturn(true);
        when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

        assertTrue(new RememberDeviceFilter(mfaFilterContext).get());
    }

    @Test
    public void should_return_false_when_adaptiveMFA_return_false_and_device_unknown() {
        final var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setSkipRememberDevice(true);
        rememberDeviceSettings.setActive(true);
        when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
        when(mfaFilterContext.isAmfaActive()).thenReturn(true);
        when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
        when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

        assertFalse(new RememberDeviceFilter(mfaFilterContext).get());
    }

    @Test
    public void should_return_false_when_adaptiveMFA_return_false_and_skipRememberDevice_disabled() {
        final var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setSkipRememberDevice(false);
        rememberDeviceSettings.setActive(true);
        when(mfaFilterContext.isMfaSkipped()).thenReturn(false);
        when(mfaFilterContext.isAmfaActive()).thenReturn(true);
        when(mfaFilterContext.isAmfaRuleTrue()).thenReturn(false);
        when(mfaFilterContext.getRememberDeviceSettings()).thenReturn(rememberDeviceSettings);

        assertFalse(new RememberDeviceFilter(mfaFilterContext).get());
    }
}
