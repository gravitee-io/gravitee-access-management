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
package io.gravitee.am.gateway.handler.manager.deviceidentifiers;

import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceIdentifierManagerTest {

    public static final String REMEMBER_DEVICE_ID = "rememberDeviceId";
    @InjectMocks
    private DeviceIdentifierManagerImpl cut = new DeviceIdentifierManagerImpl();

    @Spy
    private DeviceIdentifierProvider deviceIdentifierProvider;

    private DeviceIdentifier rememberDevice = new DeviceIdentifier();

    @Before
    public void setUp() {
        cut.getDeviceIdentifiers().put(REMEMBER_DEVICE_ID, rememberDevice);
        cut.getDeviceIdentifiersProviders().put(REMEMBER_DEVICE_ID, deviceIdentifierProvider);
    }

    @Test
    public void mustAdd_RememberDeviceIsNotActive_clientNull() {
        var map = cut.getTemplateVariables(null);
        assertFalse((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(0)).addConfigurationVariables(any(), anyString());
    }

    @Test
    public void mustAdd_RememberDeviceIsNotActive_MFASettings_null() {
        var client = new Client();
        var map = cut.getTemplateVariables(client);
        assertFalse((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(0)).addConfigurationVariables(any(), anyString());
    }

    @Test
    public void mustAdd_RememberDeviceIsNotActive_RememberDeviceSettingsNull() {
        var client = new Client();
        final MFASettings mfaSettings = new MFASettings();
        client.setMfaSettings(mfaSettings);
        var map = cut.getTemplateVariables(client);
        assertFalse((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(0)).addConfigurationVariables(any(), anyString());
    }

    @Test
    public void mustAdd_RememberDeviceIsNotActive_RememberDeviceSettings_notEnabled() {
        var client = new Client();
        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setRememberDevice(new RememberDeviceSettings());
        client.setMfaSettings(mfaSettings);
        var map = cut.getTemplateVariables(client);
        assertFalse((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(0)).addConfigurationVariables(any(), anyString());
    }

    @Test
    public void mustAdd_RememberDeviceIsNotActive_RememberDeviceSettings_noRememberDevice() {
        var client = new Client();
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        var map = cut.getTemplateVariables(client);
        assertFalse((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(0)).addConfigurationVariables(any(), anyString());
    }

    @Test
    public void mustAdd_RememberDeviceActiveAndCallProvider() {
        var client = new Client();
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setDeviceIdentifierId(REMEMBER_DEVICE_ID);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        var map = cut.getTemplateVariables(client);
        assertTrue((boolean) map.get(REMEMBER_DEVICE_IS_ACTIVE));

        verify(deviceIdentifierProvider, times(1)).addConfigurationVariables(any(), any());
    }

}
