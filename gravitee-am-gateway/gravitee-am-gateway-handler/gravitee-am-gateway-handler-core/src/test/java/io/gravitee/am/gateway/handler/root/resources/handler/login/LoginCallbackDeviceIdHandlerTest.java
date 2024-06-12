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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginCallbackDeviceIdHandlerTest extends RxWebTestBase {

    @Mock
    private ThymeleafTemplateEngine engine;
    @Mock
    private DeviceIdentifierManager deviceIdentifierManager;

    private LoginCallbackDeviceIdHandler loginCallbackDeviceIdHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        loginCallbackDeviceIdHandler =
                new LoginCallbackDeviceIdHandler(engine, deviceIdentifierManager);
    }

    @Test
    public void shouldNotGetDeviceId_optionDisabled() throws Exception {
        router.route("/login/callback")
                .order(-1)
                .handler(rc -> {
                    rc.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, false);
                    rc.next();
                });

        router.get("/login/callback")
                .handler(loginCallbackDeviceIdHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                null,
                200, "OK", null);

        verify(engine, never()).render(any(Map.class), anyString());
        verify(deviceIdentifierManager, never()).getTemplateVariables(any());
    }

    @Test
    public void shouldGetDeviceId_optionEnabled() throws Exception {
        router.route("/login/callback")
                .order(-1)
                .handler(rc -> {
                    final Client client = new Client();
                    final MFASettings mfaSettings = new MFASettings();
                    final RememberDeviceSettings rememberDeviceSettings = new RememberDeviceSettings();
                    rememberDeviceSettings.setActive(true);
                    mfaSettings.setRememberDevice(rememberDeviceSettings);
                    client.setMfaSettings(mfaSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, true);
                    rc.next();
                });

        Map deviceIdentifierVariables = new HashMap<>();
        deviceIdentifierVariables.put("providerId", "provider-id");
        when(deviceIdentifierManager.getTemplateVariables(any())).thenReturn(deviceIdentifierVariables);
        when(engine.render(anyMap(), anyString())).thenReturn(Single.just(Buffer.buffer()));

        router.get("/login/callback")
                .handler(loginCallbackDeviceIdHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                null,
                200, "OK", null);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(engine, times(1)).render(mapArgumentCaptor.capture(), anyString());
        verify(deviceIdentifierManager, times(1)).getTemplateVariables(any());
        Map<String, Object> mapData = mapArgumentCaptor.getValue();
        Assert.assertNotNull(mapData);
        Assert.assertEquals("provider-id", mapData.get("providerId"));
    }
}
