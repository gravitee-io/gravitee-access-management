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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.exception.ResourceNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceRegistrationEndpointTest {

    @Mock
    private Domain domain;

    @Mock
    private ResourceService service;

    @Mock
    private JWT jwt;

    @Mock
    private Client client;

    @Mock
    private RoutingContext context;

    @Mock
    private HttpServerResponse response;

    @Mock
    private HttpServerRequest request;

    @Mock
    private SubjectManager subjectManager;

    @InjectMocks
    private ResourceRegistrationEndpoint endpoint = new ResourceRegistrationEndpoint(domain, service, subjectManager);

    private static final String DOMAIN_PATH = "/domain";
    private static final String DOMAIN_ID = "123";
    private static final String USER_ID = "456";
    private static final String CLIENT_ID = "api";
    private static final String RESOURCE_ID = "rs_id";

    ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(client.getId()).thenReturn(CLIENT_ID);
        when(context.get(ConstantKeys.TOKEN_CONTEXT_KEY)).thenReturn(jwt);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(context.response()).thenReturn(response);
        when(response.putHeader(anyString(),anyString())).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(context.request()).thenReturn(request);
        when(request.getParam("resource_id")).thenReturn(RESOURCE_ID);
        when(subjectManager.findUserIdBySub(any())).thenReturn(Maybe.just(USER_ID));
    }

    @Test
    public void list_anyError() {
        when(service.listByDomainAndClientAndUser(anyString(), anyString(), anyString())).thenReturn(Flowable.error(new RuntimeException()));
        endpoint.handle(context);
        verify(context, times(1)).fail(errCaptor.capture());
        Assert.assertTrue("Error must be propagated", errCaptor.getValue() instanceof RuntimeException);
    }

    @Test
    public void list_noResources() {
        when(service.listByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Flowable.empty());
        endpoint.handle(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be no content status",204, intCaptor.getValue().intValue());
    }

    @Test
    public void list_withResources() {
        when(service.listByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Flowable.just(new Resource().setId(RESOURCE_ID)));
        endpoint.handle(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be ok",200, intCaptor.getValue().intValue());
    }


    @Test
    public void create_invalidResourceBody() {
        when(context.getBodyAsJson()).thenReturn(new JsonObject(Json.encode(new Resource().setId(RESOURCE_ID))));
        endpoint.create(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void create_noResource() {
        when(context.getBodyAsJson()).thenReturn(new JsonObject("{\"id\":\"rs_id\",\"resource_scopes\":[\"scope\"]}"));
        when(service.create(any() , eq(DOMAIN_ID), eq(CLIENT_ID), eq(USER_ID))).thenReturn(Single.error(new ResourceNotFoundException(RESOURCE_ID)));
        endpoint.create(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof ResourceNotFoundException);
    }

    @Test
    public void create_withResource() {
        ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);
        when(context.get(CONTEXT_PATH)).thenReturn(DOMAIN_PATH);
        when(context.getBodyAsJson()).thenReturn(new JsonObject("{\"id\":\"rs_id\",\"resource_scopes\":[\"scope\"]}"));
        when(service.create(any() , eq(DOMAIN_ID), eq(CLIENT_ID), eq(USER_ID))).thenReturn(Single.just(new Resource().setId(RESOURCE_ID)));
        when(request.host()).thenReturn("host");
        when(request.scheme()).thenReturn("http");
        endpoint.create(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).putHeader(eq(HttpHeaders.LOCATION),strCaptor.capture());
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be created",201, intCaptor.getValue().intValue());
        Assert.assertEquals("Location", "http://host"+DOMAIN_PATH+"/uma/protection/resource_set/"+RESOURCE_ID, strCaptor.getValue());
    }

    @Test
    public void get_noResource() {
        when(service.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        endpoint.get(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof ResourceNotFoundException);
    }

    @Test
    public void get_withResource() {
        when(service.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource().setId(RESOURCE_ID)));
        endpoint.get(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be ok",200, intCaptor.getValue().intValue());
    }

    @Test
    public void update_invalidResourceBody() {
        when(context.getBodyAsJson()).thenReturn(new JsonObject("{\"description\":\"mydescription\"}"));
        endpoint.update(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof InvalidRequestException);
    }

    @Test
    public void update_noResource() {
        when(context.getBodyAsJson()).thenReturn(new JsonObject("{\"id\":\"rs_id\",\"resource_scopes\":[\"scope\"]}"));
        when(service.update(any() , eq(DOMAIN_ID), eq(CLIENT_ID), eq(USER_ID), eq(RESOURCE_ID))).thenReturn(Single.error(new ResourceNotFoundException(RESOURCE_ID)));
        endpoint.update(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof ResourceNotFoundException);
    }

    @Test
    public void update_withResource() {
        when(context.getBodyAsJson()).thenReturn(new JsonObject("{\"id\":\"rs_id\",\"resource_scopes\":[\"scope\"]}"));
        when(service.update(any() , eq(DOMAIN_ID), eq(CLIENT_ID), eq(USER_ID), eq(RESOURCE_ID))).thenReturn(Single.just(new Resource()));
        endpoint.update(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be ok",200, intCaptor.getValue().intValue());
    }

    @Test
    public void delete_noResource() {
        when(service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Completable.error(new ResourceNotFoundException(RESOURCE_ID)));
        endpoint.delete(context);
        verify(context).fail(errCaptor.capture());
        Assert.assertTrue(errCaptor.getValue() instanceof ResourceNotFoundException);
    }

    @Test
    public void delete_withResource() {
        when(service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Completable.complete());
        endpoint.delete(context);
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        Assert.assertEquals("Should be no content status",204, intCaptor.getValue().intValue());
    }
}
