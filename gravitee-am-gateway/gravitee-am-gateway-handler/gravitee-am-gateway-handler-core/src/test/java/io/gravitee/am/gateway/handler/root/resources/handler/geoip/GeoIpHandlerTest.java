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

package io.gravitee.am.gateway.handler.root.resources.handler.geoip;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.MockHttpServerRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.http.HttpServerRequest;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GeoIpHandlerTest {

    @Mock
    private EventBus eventBus;

    @Mock
    private UserActivityService userActivityService;

    @Spy
    private SpyRoutingContext routingContext;
    private HashMap<Object, Object> data;
    private Client client;

    private GeoIpHandler geoIpHandler;
    private HttpServerRequest request;

    @Before
    public void setUp() {
        geoIpHandler = new GeoIpHandler(userActivityService, eventBus);
        client = new Client();
        client.setMfaSettings(new MFASettings());
        doReturn(client).when(routingContext).get(ConstantKeys.CLIENT_CONTEXT_KEY);
        doReturn(false).when(userActivityService).canSaveUserActivity();

        data = new HashMap<>();
        doReturn(data).when(routingContext).data();

        request = new MockHttpServerRequest();
        doReturn(request).when(routingContext).request();

        doNothing().when(routingContext).next();
    }

    @Test
    public void mustNotPerformGeoIp_noAdaptiveRule() {
        geoIpHandler.handle(routingContext);
        verify(routingContext, times(1)).next();
        verify(routingContext, times(0)).data();
    }

    @Test
    public void mustNotPerformGeoIp_dataAlreadyExist() {
        data.put(GEOIP_KEY, new JsonObject());
        client.getMfaSettings().setAdaptiveAuthenticationRule("{#object = 'value'}");
        geoIpHandler.handle(routingContext);
        verify(routingContext, times(1)).next();
        verify(routingContext, times(1)).data();
    }

    @Test
    public void mustNotPerformGeoIp_noIp() {
        client.getMfaSettings().setAdaptiveAuthenticationRule("{#object = 'value'}");

        geoIpHandler.handle(routingContext);
        verify(routingContext, times(1)).next();
        verify(routingContext, times(1)).data();
    }

    @Test
    public void mustPerformGeoIp_ipIsThere() {
        client.getMfaSettings().setAdaptiveAuthenticationRule("{#object = 'value'}");
        request.getDelegate().headers().add(HttpHeaders.X_FORWARDED_FOR, "55.55.55.55");
        geoIpHandler.handle(routingContext);
        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void mustPerformGeoIp_ipIsThere_with_userActivity() {
        doReturn(true).when(userActivityService).canSaveUserActivity();
        request.getDelegate().headers().add(HttpHeaders.X_FORWARDED_FOR, "55.55.55.55");
        geoIpHandler.handle(routingContext);
        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

}
