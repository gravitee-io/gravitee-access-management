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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

import static jakarta.ws.rs.HttpMethod.PATCH;
import static org.glassfish.jersey.client.HttpUrlConnectorProvider.SET_METHOD_WORKAROUND;
import org.junit.jupiter.api.Test;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class ProtectedResourceResourceTest extends JerseySpringTest {

    private Response patch(WebTarget webTarget, PatchProtectedResource patchRequest) {
        try {
            String jsonBody = objectMapper.writeValueAsString(patchRequest);
            return webTarget.request()
                    .property(SET_METHOD_WORKAROUND, true)
                    .method(PATCH, Entity.entity(jsonBody, MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void shouldReturnProtectedResource(){
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResourcePrimaryData protectedResource = new ProtectedResourcePrimaryData(
                "id", "clientId", "name", "desc", ProtectedResource.Type.MCP_SERVER, List.of("https://onet.pl"), List.of(), new Date());

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(protectedResource))
                .when(protectedResourceService).findByDomainAndIdAndType(domainId, "id", ProtectedResource.Type.MCP_SERVER);


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = response.readEntity(Map.class);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals("id", data.get("id"));
        assertEquals("name", data.get("name"));
    }

    @Test
    public void shouldNotReturnProtectedResource_404(){
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty())
                .when(protectedResourceService).findByDomainAndIdAndType(domainId, "id", ProtectedResource.Type.MCP_SERVER);


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteProtectedResource_404_notFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        // delete fails with not found (service validates domain/type)
        doReturn(Completable.error(new ProtectedResourceNotFoundException("id")))
                .when(protectedResourceService).delete(eq(mockDomain), eq("id"), eq(ProtectedResource.Type.MCP_SERVER), any());

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteProtectedResource_204() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        // permission ok
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        // delete (service validates domain/type)
        doReturn(Completable.complete())
                .when(protectedResourceService).delete(eq(mockDomain), eq("id"), eq(ProtectedResource.Type.MCP_SERVER), any());

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldDeleteProtectedResource_403() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        // permission denied via helper path
        doReturn(io.reactivex.rxjava3.core.Single.just(false)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        // do not stub findByDomainAndIdAndType or delete as request should be rejected before

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_basicTest() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setName("NewName");
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("NewName"));

        // permission ok
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        ProtectedResourcePrimaryData result = readEntity(response, ProtectedResourcePrimaryData.class);
        assertEquals("resource-id", result.id());
        assertEquals("NewName", result.name());
    }

    @Test
    public void shouldPatchProtectedResource_404_domainNotFound() {
        final String domainId = "domain-1";

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("NewName"));

        // permission ok
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_403_forbidden() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("NewName"));

        // permission denied
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_400_badRequest() {
        PatchProtectedResource patchRequest = new PatchProtectedResource();

        final Response response = patch(target("domains")
                .path("domain-id")
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_withValidName_200() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setName("test-server");
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("test-server"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals("Valid name 'test-server' should return 200", HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_withEmptyOptional_200() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setName("ExistingName");
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.empty());
        patchRequest.setDescription(Optional.of("Updated description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals("Empty optional (don't update) should return 200", HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldUpdateProtectedResource_withValidName_200() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setName("test-server");
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("test-server");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).update(any(Domain.class), eq("resource-id"), any(UpdateProtectedResource.class), any());

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id")
                .request()
                .put(Entity.json(updateRequest));

        assertEquals("Valid name 'test-server' should return 200", HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_returnsProtectedResourcePrimaryData() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setClientId("client-id");
        updatedResource.setName("test-server");
        updatedResource.setDescription("description");
        updatedResource.setType(ProtectedResource.Type.MCP_SERVER);
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));
        updatedResource.setUpdatedAt(new Date());

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("test-server"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        String responseBody = response.readEntity(String.class);
        assertFalse("Response should not contain 'domainId' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"domainId\""));
        assertFalse("Response should not contain 'createdAt' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"createdAt\""));
        assertFalse("Response should not contain 'clientSecrets' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"clientSecrets\""));
        assertFalse("Response should not contain 'secretSettings' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"secretSettings\""));

        ProtectedResourcePrimaryData result;
        try {
            result = objectMapper.readValue(responseBody, ProtectedResourcePrimaryData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals("resource-id", result.id());
        assertEquals("client-id", result.clientId());
        assertEquals("test-server", result.name());
        assertEquals("description", result.description());
        assertEquals(ProtectedResource.Type.MCP_SERVER, result.type());
    }

    @Test
    public void shouldUpdateProtectedResource_returnsProtectedResourcePrimaryData() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setClientId("client-id");
        updatedResource.setName("test-server");
        updatedResource.setDescription("description");
        updatedResource.setType(ProtectedResource.Type.MCP_SERVER);
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));
        updatedResource.setUpdatedAt(new Date());

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("test-server");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).update(any(Domain.class), eq("resource-id"), any(UpdateProtectedResource.class), any());

        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id")
                .request()
                .put(Entity.json(updateRequest));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        String responseBody = response.readEntity(String.class);
        assertFalse("Response should not contain 'domainId' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"domainId\""));
        assertFalse("Response should not contain 'createdAt' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"createdAt\""));
        assertFalse("Response should not contain 'clientSecrets' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"clientSecrets\""));
        assertFalse("Response should not contain 'secretSettings' field (not in ProtectedResourcePrimaryData)", responseBody.contains("\"secretSettings\""));

        ProtectedResourcePrimaryData result;
        try {
            result = objectMapper.readValue(responseBody, ProtectedResourcePrimaryData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals("resource-id", result.id());
        assertEquals("client-id", result.clientId());
        assertEquals("test-server", result.name());
        assertEquals("description", result.description());
        assertEquals(ProtectedResource.Type.MCP_SERVER, result.type());
    }

}