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

package io.gravitee.am.gateway.handler.root.resources.handler.rememberdevice;

import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_TYPE;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceIdentifierHandlerTest {

    private DeviceService deviceService;

    private SpyRoutingContext spyRoutingContext;
    private Client client;
    private DeviceIdentifierHandler handler;
    private String userId;
    private String deviceIdentifierId;

    @Before
    public void setUp() {
        Domain domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        client = new Client();
        client.setDomain(domain.getId());
        client.setId(UUID.randomUUID().toString());
        client.setClientId(UUID.randomUUID().toString());
        deviceService = spy(DeviceService.class);
        handler = new DeviceIdentifierHandler(deviceService);

        userId = UUID.randomUUID().toString();
        deviceIdentifierId = UUID.randomUUID().toString();
        spyRoutingContext = spy(new SpyRoutingContext());
        doNothing().when(spyRoutingContext).next();
    }

    @Test
    public void mustDoNext_nullClient() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_nullUser() {
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);

        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_nullMfa() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);

        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_nullRememberDeviceSettings() {
        client.setMfaSettings(new MFASettings());
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);

        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_RememberDeviceSettingsIsNotActive() {
        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setRememberDevice(new RememberDeviceSettings());
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_deviceIdIsEmpty() {
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setDeviceIdentifierId(deviceIdentifierId);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);

        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(deviceService, times(0)).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    public void mustDoNext_DeviceAlreadyExists() {
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setDeviceIdentifierId(UUID.randomUUID().toString());
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);
        spyRoutingContext.putParam(DEVICE_ID, "deviceId");

        doReturn(Single.just(false)).when(deviceService).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
        handler.handle(spyRoutingContext);

        Assert.assertTrue(spyRoutingContext.session().get(DEVICE_ALREADY_EXISTS_KEY));
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_DeviceNotExists() {
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setDeviceIdentifierId(UUID.randomUUID().toString());
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);

        final User user = new User();
        user.setId(userId);
        spyRoutingContext.put(USER_CONTEXT_KEY, user);
        spyRoutingContext.putParam(DEVICE_ID, "deviceId2");
        spyRoutingContext.putParam(DEVICE_TYPE, "deviceType");

        doReturn(Single.just(true)).when(deviceService).deviceExists(anyString(), anyString(), any(), anyString(), anyString());
        handler.handle(spyRoutingContext);

        Assert.assertFalse(spyRoutingContext.session().get(DEVICE_ALREADY_EXISTS_KEY));
        Assert.assertEquals(spyRoutingContext.session().get(DEVICE_ID), "deviceId2");
        Assert.assertEquals(spyRoutingContext.session().get(DEVICE_TYPE), "deviceType");
        verify(spyRoutingContext, times(1)).next();
    }
}
