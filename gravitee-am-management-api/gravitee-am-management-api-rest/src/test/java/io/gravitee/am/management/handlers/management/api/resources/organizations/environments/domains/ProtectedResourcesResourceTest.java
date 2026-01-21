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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceSecret;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

public class ProtectedResourcesResourceTest extends JerseySpringTest {

    @Test
    public void shouldCreate() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setName("name");
        newProtectedResource.setResourceIdentifiers(List.of("https://something.pl"));
        newProtectedResource.setDescription("a description");

        ProtectedResourceSecret protectedResourceSecret = new ProtectedResourceSecret();
        protectedResourceSecret.setId("app-id");
        protectedResourceSecret.setName("name");
        protectedResourceSecret.setClientSecret("client-secret");
        protectedResourceSecret.setClientId("client-id");
        newProtectedResource.setType("MCP_SERVER");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(protectedResourceSecret)).when(protectedResourceService).create(any(Domain.class), any(User.class), any(NewProtectedResource.class));

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        ProtectedResourceSecret result = objectMapper.readValue(response.readEntity(String.class), ProtectedResourceSecret.class);
        assertEquals(protectedResourceSecret.getName(), result.getName());
        assertEquals(protectedResourceSecret.getId(), result.getId());
        assertEquals(protectedResourceSecret.getClientId(), result.getClientId());
        assertEquals(protectedResourceSecret.getClientSecret(), result.getClientSecret());

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldReturnBadRequest_missingResourceIdentifier() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setName("name");
        newProtectedResource.setDescription("a description");
        newProtectedResource.setType("MCP_SERVER");


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[resourceIdentifiers: must not be empty]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldReturnBadRequest_missingResourceIdentifier2() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setName("name");
        newProtectedResource.setDescription("a description");
        newProtectedResource.setResourceIdentifiers(List.of(""));
        newProtectedResource.setType("MCP_SERVER");


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[: must not be blank]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldReturnBadRequest_wrongResourceIdentifier() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setResourceIdentifiers(List.of("pl"));
        newProtectedResource.setDescription("a description");
        newProtectedResource.setName("name");
        newProtectedResource.setType("MCP_SERVER");

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[pl: Invalid URL]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldReturnBadRequest_missingName() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setResourceIdentifiers(List.of("https://something.pl"));
        newProtectedResource.setDescription("a description");
        newProtectedResource.setType("MCP_SERVER");


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[name: must not be blank]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldCreateProtectedResource_withValidName_201() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setName("test-server");
        newProtectedResource.setResourceIdentifiers(List.of("https://example.com"));
        newProtectedResource.setType("MCP_SERVER");

        ProtectedResourceSecret protectedResourceSecret = new ProtectedResourceSecret();
        protectedResourceSecret.setId("resource-id");
        protectedResourceSecret.setName("test-server");
        protectedResourceSecret.setClientSecret("client-secret");
        protectedResourceSecret.setClientId("client-id");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(protectedResourceSecret)).when(protectedResourceService).create(any(Domain.class), any(User.class), any(NewProtectedResource.class));

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        assertEquals("Valid name 'test-server' should return 201", HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldReturnBadRequest_missingType() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setResourceIdentifiers(List.of("https://something.pl"));
        newProtectedResource.setDescription("a description");
        newProtectedResource.setName("name");

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[type: must not be empty]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldReturnBadRequest_wrongType() throws JsonProcessingException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setResourceIdentifiers(List.of("https://something.pl"));
        newProtectedResource.setDescription("a description");
        newProtectedResource.setName("name");
        newProtectedResource.setType("MCP_SERVERR");

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        Map map = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertEquals("[MCP_SERVERR: Available types: [MCP_SERVER]]", map.get("message"));
        assertEquals(400, map.get("http_status"));
    }

    @Test
    public void shouldReturnProtectedResourceDomainList(){
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("SECRETVALUE");

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId("app-id");
        protectedResource.setName("name");
        protectedResource.setClientSecrets(List.of(clientSecret));
        protectedResource.setClientId("client-id");
        protectedResource.setUpdatedAt(new Date());

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Page(List.of(protectedResource), 0, 1)))
                .when(protectedResourceService).findByDomainAndType(eq(domainId), eq(ProtectedResource.Type.MCP_SERVER), any());


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        Map map = response.readEntity(Map.class);
        Map data = (Map) ((List)map.get("data")).get(0);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals("app-id", data.get("id"));
        assertEquals("name", data.get("name"));
    }

    @Test
    public void shouldApplyDefaultSortValue(){
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("SECRETVALUE");

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId("app-id");
        protectedResource.setName("name");
        protectedResource.setClientSecrets(List.of(clientSecret));
        protectedResource.setClientId("client-id");
        protectedResource.setUpdatedAt(new Date());

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Page(List.of(protectedResource), 0, 1)))
                .when(protectedResourceService).findByDomainAndType(eq(domainId), eq(ProtectedResource.Type.MCP_SERVER), any());


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        Map map = response.readEntity(Map.class);
        Map data = (Map) ((List)map.get("data")).get(0);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals("app-id", data.get("id"));
        assertEquals("name", data.get("name"));

        Mockito.verify(protectedResourceService, Mockito.times(1)).findByDomainAndType(
                eq(domainId),
                eq(ProtectedResource.Type.MCP_SERVER),
                argThat(sort -> sort.getSortBy().get().equals("updatedAt") && sort.direction() == -1));
    }


    @Test
    public void shouldApplySortValue(){
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("SECRETVALUE");

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId("app-id");
        protectedResource.setName("name");
        protectedResource.setClientSecrets(List.of(clientSecret));
        protectedResource.setClientId("client-id");
        protectedResource.setUpdatedAt(new Date());

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Page(List.of(protectedResource), 0, 1)))
                .when(protectedResourceService).findByDomainAndType(eq(domainId), eq(ProtectedResource.Type.MCP_SERVER), any());


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .queryParam("type", "MCP_SERVER")
                .queryParam("sort", "name.asc")
                .request().get();

        Map map = response.readEntity(Map.class);
        Map data = (Map) ((List)map.get("data")).get(0);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals("app-id", data.get("id"));
        assertEquals("name", data.get("name"));

        Mockito.verify(protectedResourceService, Mockito.times(1)).findByDomainAndType(
                eq(domainId),
                eq(ProtectedResource.Type.MCP_SERVER),
                argThat(sort -> sort.getSortBy().get().equals("name") && sort.direction() == 1));
    }

}