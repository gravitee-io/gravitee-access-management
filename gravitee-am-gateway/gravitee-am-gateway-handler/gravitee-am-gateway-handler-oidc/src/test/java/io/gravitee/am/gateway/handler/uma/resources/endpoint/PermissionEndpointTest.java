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
package io.gravitee.am.gateway.handler.uma.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionEndpointTest {

    @Mock
    private Domain domain;

    @Mock
    private PermissionTicketService permissionTicketService;

    @Mock
    private JWT jwt;

    @Mock
    private Client client;

    @Mock
    private RoutingContext context;

    @Mock
    private HttpServerResponse response;

    @InjectMocks
    private PermissionEndpoint endpoint = new PermissionEndpoint(domain, permissionTicketService);

    private static final String DOMAIN_ID = "123";
    private static final String CLIENT_ID = "api";

    ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);
    ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);

    @Before
    public void setUp() {
        when(context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY)).thenReturn(jwt);
        when(context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(client.getId()).thenReturn(CLIENT_ID);
    }

    @Test
    public void handle_unableToParse() {
        when(context.getBody()).thenReturn(null);
        endpoint.handle(context);
        verify(context,times(1)).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void handle_missingResourceId() {
        when(context.getBody()).thenReturn(Buffer.buffer("{\"unknown\":\"object\"}"));
        endpoint.handle(context);
        verify(context,times(1)).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void handle_scopeIsEmpty() {
        when(context.getBody()).thenReturn(Buffer.buffer("{\"resource_id\":\"id\", \"resource_scopes\":[\"\"]}"));
        endpoint.handle(context);
        verify(context,times(1)).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void success_simpleRequest() {
        PermissionTicket success = new PermissionTicket().setId("success");
        final String simpleRequest = "{\"resource_id\":\"{{set_one}}\", \"resource_scopes\":[\"profile:read\"]}";

        when(context.getBody()).thenReturn(Buffer.buffer(simpleRequest));
        when(context.response()).thenReturn(response);
        when(response.putHeader(anyString(),anyString())).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(permissionTicketService.create(anyList(), eq(DOMAIN_ID), eq(CLIENT_ID))).thenReturn(Single.just(success));
        endpoint.handle(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(context.response(), times(1)).setStatusCode(intCaptor.capture());
        verify(context.response(), times(1)).end(strCaptor.capture());
        Assert.assertEquals("Expecting 201 creation status",intCaptor.getValue().intValue(),201);
        Assert.assertTrue("Expect success id", strCaptor.getValue().contains("success"));
    }

    @Test
    public void success_extendedRequest() {
        PermissionTicket success = new PermissionTicket().setId("success");
        final String extendedRequest = "[{\"resource_id\":\"{{set_one}}\", \"resource_scopes\":[\"profile:read\"]}, {\"resource_id\":\"{{set_two}}\",\"resource_scopes\":[\"avatar:write\"]}]";

        when(context.getBody()).thenReturn(Buffer.buffer(extendedRequest));
        when(context.response()).thenReturn(response);
        when(response.putHeader(anyString(),anyString())).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(permissionTicketService.create(anyList(), eq(DOMAIN_ID), eq(CLIENT_ID))).thenReturn(Single.just(success));
        endpoint.handle(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(context.response(), times(1)).setStatusCode(intCaptor.capture());
        verify(context.response(), times(1)).end(strCaptor.capture());
        Assert.assertEquals("Expecting 201 creation status",intCaptor.getValue().intValue(),201);
        Assert.assertTrue("Expect success id", strCaptor.getValue().contains("success"));
    }
}
