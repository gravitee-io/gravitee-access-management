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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint;

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.request.DynamicClientRegistrationRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.ClientService;
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
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationEndpointTest {

    @Mock
    private ClientService clientService;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private DynamicClientRegistrationService dcrService;

    @InjectMocks
    private DynamicClientRegistrationEndpoint endpoint = new DynamicClientRegistrationEndpoint(dcrService, clientService, clientSyncService);

    @Mock
    private DynamicClientRegistrationRequest request;

    @Mock
    private JsonObject json;

    @Mock
    private RoutingContext routingContext;

    @Before
    public void setUp() {
        when(json.mapTo(DynamicClientRegistrationRequest.class)).thenReturn(request);
        when(routingContext.get("domain")).thenReturn("domain-for-test");
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
    public void register_invalidRequest() {
        //Context
        when(dcrService.validateClientRegistrationRequest(any())).thenReturn(Single.error(new Exception()));

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).fail(any());
    }

    @Test
    public void register_fail() {
        //Context
        when(dcrService.validateClientRegistrationRequest(any())).thenReturn(Single.just(request));
        when(clientService.create(any())).thenReturn(Single.error(new Exception()));

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

        when(dcrService.create(any(),any())).thenReturn(new Client());
        when(dcrService.validateClientRegistrationRequest(any())).thenReturn(Single.just(request));
        when(dcrService.applyDefaultIdentityProvider(any())).thenReturn(Single.just(new Client()));
        when(dcrService.applyDefaultCertificateProvider(any())).thenReturn(Single.just(new Client()));
        when(dcrService.applyRegistrationAccessToken(any(),any())).thenReturn(Single.just(new Client()));
        when(clientService.create(any())).thenReturn(Single.just(new Client()));
        when(clientSyncService.addDynamicClientRegistred(any())).thenReturn(new Client());

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).response();
        verify(serverResponse,times(3)).putHeader(anyString(),anyString());
        verify(serverResponse,times(1)).end(anyString());
    }
}
