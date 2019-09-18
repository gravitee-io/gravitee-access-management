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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationEndpointTest {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private DynamicClientRegistrationService dcrService;

    @InjectMocks
    private DynamicClientRegistrationEndpoint endpoint = new DynamicClientRegistrationEndpoint(dcrService, clientSyncService);

    @Mock
    private DynamicClientRegistrationRequest request;

    @Mock
    private JsonObject json;

    @Mock
    private RoutingContext routingContext;

    @Before
    public void setUp() {
        when(json.mapTo(DynamicClientRegistrationRequest.class)).thenReturn(request);
        when(routingContext.getBodyAsJson()).thenReturn(json);
    }

    @Test
    public void register_decodeException() {
        //Context
        when(routingContext.getBodyAsJson()).thenThrow(new DecodeException());

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).fail(any());
    }

    @Test
    public void register_invalidRequestFormat() {
        //Context
        when(routingContext.getBodyAsJson()).thenReturn(null);

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).fail(any());
    }

    @Test
    public void register_fail() {
        //Context
        HttpServerRequest serverRequest = Mockito.mock(HttpServerRequest.class);
        when(routingContext.request()).thenReturn(serverRequest);
        when(dcrService.create(any(),any())).thenReturn(Single.error(new Exception()));

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).fail(any());
    }

    @Test
    public void register_success() {
        //Context
        HttpServerRequest serverRequest = Mockito.mock(HttpServerRequest.class);
        HttpServerResponse serverResponse = Mockito.mock(HttpServerResponse.class);

        when(routingContext.request()).thenReturn(serverRequest);
        when(serverRequest.getHeader(any())).thenReturn(null);
        when(serverRequest.scheme()).thenReturn("https");
        when(serverRequest.host()).thenReturn("host");
        when(routingContext.response()).thenReturn(serverResponse);
        when(serverResponse.putHeader(anyString(),anyString())).thenReturn(serverResponse);
        when(serverResponse.setStatusCode(201)).thenReturn(serverResponse);

        when(dcrService.create(any(),any())).thenReturn(Single.just(new Client()));
        when(clientSyncService.addDynamicClientRegistred(any())).thenReturn(new Client());

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).response();
        verify(serverResponse,times(3)).putHeader(anyString(),anyString());
        verify(serverResponse,times(1)).end(anyString());
    }
}
