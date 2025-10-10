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
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.ProtectedResourceSecret;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

        ProtectedResourceSecret protectedResource = new ProtectedResourceSecret();
        protectedResource.setId("app-id");
        protectedResource.setName("name");
        protectedResource.setClientSecret("client-secret");
        protectedResource.setClientId("client-id");
        newProtectedResource.setType("MCP_SERVER");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(protectedResource)).when(protectedResourceService).create(any(Domain.class), any(User.class), any(NewProtectedResource.class));

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .request().post(Entity.json(newProtectedResource));

        ProtectedResourceSecret result = objectMapper.readValue(response.readEntity(String.class), ProtectedResourceSecret.class);
        assertEquals(protectedResource.getName(), result.getName());
        assertEquals(protectedResource.getId(), result.getId());
        assertEquals(protectedResource.getClientId(), result.getClientId());
        assertEquals(protectedResource.getClientSecret(), result.getClientSecret());

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

}