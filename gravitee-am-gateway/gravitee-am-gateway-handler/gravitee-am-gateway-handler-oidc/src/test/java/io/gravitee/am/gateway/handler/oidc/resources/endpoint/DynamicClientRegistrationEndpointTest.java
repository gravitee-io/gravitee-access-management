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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.HostAndPort;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RequestBody;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

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

    @Mock
    private RequestBody requestBody;

    @Before
    public void setUp() {
        when(json.mapTo(DynamicClientRegistrationRequest.class)).thenReturn(request);
        when(routingContext.body()).thenReturn(requestBody);
        when(requestBody.asJsonObject()).thenReturn(json);
    }

    @Test
    public void register_decodeException() {
        //Context
        when(requestBody.asJsonObject()).thenThrow(new DecodeException());

        //Test
        endpoint.handle(routingContext);

        //Assertions
        verify(routingContext, times(1)).fail(any());
    }

    @Test
    public void register_invalidRequestFormat() {
        //Context
        when(requestBody.asJsonObject()).thenReturn(null);

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

    @Test
    public void register_authorizationDetailsTypesNotAnArray() {
        givenBody(Map.of("authorization_details_types", "payment_initiation"));

        endpoint.handle(routingContext);

        verifyRejectedWith("authorization_details_types: invalid value");
    }

    @Test
    public void register_authorizationDetailsTypesHoldingAnObject() {
        givenBody(Map.of("authorization_details_types", List.of(Map.of("type", "payment_initiation"))));

        endpoint.handle(routingContext);

        verifyRejectedWith("authorization_details_types: invalid value");
    }

    @Test
    public void register_redirectUrisNotAnArray() {
        givenBody(Map.of("redirect_uris", "https://callback"));

        endpoint.handle(routingContext);

        verifyRejectedWith("redirect_uris: invalid value");
    }

    private void givenBody(Map<String, Object> body) {
        when(json.mapTo(DynamicClientRegistrationRequest.class))
                .thenAnswer(invocation -> MAPPER.convertValue(body, DynamicClientRegistrationRequest.class));
    }

    private void verifyRejectedWith(String expectedMessage) {
        verify(routingContext).fail(argThat(error -> error instanceof InvalidClientMetadataException
                && expectedMessage.equals(error.getMessage())));
        verify(dcrService, never()).create(any(), any());
    }
}
