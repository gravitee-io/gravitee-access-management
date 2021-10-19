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

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.http.HttpMethod;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.*;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RememberDeviceHandlerTest extends RxWebTestBase {

    private SpyRoutingContext spyRoutingContext;
    private Client client;
    private RememberDeviceHandler handler;

    @Before
    public void setUp() {
        domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        client = new Client();
        client.setId(UUID.randomUUID().toString());
        client.setClientId(UUID.randomUUID().toString());

        handler = new RememberDeviceHandler();

        spyRoutingContext = spy(new SpyRoutingContext());
        doNothing().when(spyRoutingContext).next();
        doNothing().when(spyRoutingContext).fail(anyInt());
    }

    @Test
    public void mustFail_methodNotAllowed() {
        spyRoutingContext.setMethod(HttpMethod.OPTIONS);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(1)).fail(405);
        verify(spyRoutingContext, times(0)).next();
    }

    @Test
    public void mustDoNext_POSTMethod() {
        spyRoutingContext.setMethod(HttpMethod.POST);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_nullClient() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_nullMfa() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_nullRememberDeviceSettings() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        client.setMfaSettings(new MFASettings());
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_RememberDeviceSettingsIsNotActive() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setRememberDevice(new RememberDeviceSettings());
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_deviceAlreadyExist() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
        handler.handle(spyRoutingContext);

        Assert.assertNull(spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS));
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_defaultDeviceConsentTimeSecond() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
        handler.handle(spyRoutingContext);

        Assert.assertEquals((long) spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS), 7200L);
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNext_setDeviceConsentTimeSecond() {
        spyRoutingContext.setMethod(HttpMethod.GET);
        final MFASettings mfaSettings = new MFASettings();
        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setExpirationTimeSeconds(300L);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);
        spyRoutingContext.put(CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
        handler.handle(spyRoutingContext);

        Assert.assertEquals((long) spyRoutingContext.get(REMEMBER_DEVICE_CONSENT_TIME_SECONDS), 300L);
        verify(spyRoutingContext, times(0)).fail(405);
        verify(spyRoutingContext, times(1)).next();
    }
}
