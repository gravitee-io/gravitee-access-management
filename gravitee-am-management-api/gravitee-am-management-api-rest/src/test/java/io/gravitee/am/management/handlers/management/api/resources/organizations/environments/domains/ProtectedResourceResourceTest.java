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
import io.gravitee.am.model.ProtectedResourceSecret;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateMcpTool;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.model.NewProtectedResource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
                "id", "clientId", "name", "desc", ProtectedResource.Type.MCP_SERVER, List.of("https://onet.pl"), null, List.of(), List.of(), new Date(), "Certificate");

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
    public void shouldPatchProtectedResource_400_fragmentInResourceIdentifier() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        // PATCH request with fragment in resource identifier (should be rejected)
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.of(List.of("https://api.example.com/resource#fragment")));

        // permission ok
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        // Fragment should be rejected by @Url(allowFragment = false) validation
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldPatchProtectedResource_400_invalidFeatureKeyPattern() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        // PATCH request with invalid feature key (contains spaces)
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        UpdateMcpTool invalidKeyTool = new UpdateMcpTool();
        invalidKeyTool.setKey("invalid key with spaces"); // Should be rejected by @Pattern
        invalidKeyTool.setDescription("Tool with invalid key");
        patchRequest.setFeatures(Optional.of(List.of(invalidKeyTool)));

        // permission ok
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = patch(target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id"), patchRequest);

        // Invalid feature key pattern should be rejected by @Pattern validation
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
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

    @Test
    public void shouldUpdateProtectedResource_acceptPayloadWithReadOnlyFields_200() throws IOException {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        Map<String, Object> feature = new HashMap<>();
        feature.put("key", "k1");
        feature.put("description", "d1");
        feature.put("type", "mcp_tool");
        feature.put("scopes", List.of());
        feature.put("createdAt", 1770766870952L);
        feature.put("updatedAt", 1770766870952L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "test-server");
        body.put("resourceIdentifiers", List.of("https://example.com"));
        body.put("createdAt", 123L);
        body.put("updatedAt", 456L);
        body.put("features", List.of(feature));

        ProtectedResource updatedResource = new ProtectedResource();
        updatedResource.setId("resource-id");
        updatedResource.setDomainId(domainId);
        updatedResource.setName("test-server");
        updatedResource.setResourceIdentifiers(List.of("https://example.com"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                .when(protectedResourceService).update(any(Domain.class), eq("resource-id"), any(UpdateProtectedResource.class), any());

        Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("resource-id")
                .request()
                .put(Entity.entity(objectMapper.writeValueAsString(body), MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        verify(protectedResourceService).update(any(Domain.class), eq("resource-id"), any(UpdateProtectedResource.class), any());
    }

    /**
     * Operation types for testing (shared across validation test classes)
     */
    enum Operation {
        CREATE, UPDATE, PATCH
    }

    /**
     * Shared helper methods for validation tests
     */
    private Response performOperation(Operation operation, String name) {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        // Setup common mocks
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
        doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        return switch (operation) {
            case CREATE -> {
                NewProtectedResource resource = createValidNewProtectedResource();
                resource.setName(name);

                ProtectedResource createdResource = new ProtectedResource();
                createdResource.setId("new-id");
                createdResource.setDomainId(domainId);
                createdResource.setName(name);
                createdResource.setResourceIdentifiers(List.of("https://example.com"));

                // CREATE returns ProtectedResourceSecret (which includes the secret)
                ProtectedResourceSecret secretResponse = ProtectedResourceSecret.from(createdResource, "test-secret");
                doReturn(Single.just(secretResponse))
                        .when(protectedResourceService).create(any(Domain.class), any(), any(NewProtectedResource.class));

                yield target("domains")
                        .path(domainId)
                        .path("protected-resources")
                        .request()
                        .post(Entity.json(resource));
            }
            case UPDATE -> {
                UpdateProtectedResource resource = createValidUpdateProtectedResource();
                resource.setName(name);

                ProtectedResource updatedResource = new ProtectedResource();
                updatedResource.setId("resource-id");
                updatedResource.setDomainId(domainId);
                updatedResource.setName(name);
                updatedResource.setResourceIdentifiers(List.of("https://example.com"));

                doReturn(Single.just(ProtectedResourcePrimaryData.of(updatedResource)))
                        .when(protectedResourceService).update(any(Domain.class), eq("resource-id"), any(UpdateProtectedResource.class), any());

                yield target("domains")
                        .path(domainId)
                        .path("protected-resources")
                        .path("resource-id")
                        .request()
                        .put(Entity.json(resource));
            }
            case PATCH -> {
                PatchProtectedResource resource = new PatchProtectedResource();
                resource.setName(Optional.of(name));

                ProtectedResource patchedResource = new ProtectedResource();
                patchedResource.setId("resource-id");
                patchedResource.setDomainId(domainId);
                patchedResource.setName(name);
                patchedResource.setResourceIdentifiers(List.of("https://example.com"));

                doReturn(Single.just(ProtectedResourcePrimaryData.of(patchedResource)))
                        .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

                yield patch(target("domains")
                        .path(domainId)
                        .path("protected-resources")
                        .path("resource-id"), resource);
            }
        };
    }

    /**
     * Verify error message format - reusable assertion for validation tests
     */
    private void verifyErrorMessage(Response response, String expectedMessage) {
        @SuppressWarnings("unchecked")
        Map<String, Object> error = response.readEntity(Map.class);
        assertNotNull("Error response should not be null", error);
        assertNotNull("Error response should have message", error.get("message"));
        assertThat(error.get("message").toString())
                .as("Error message should contain expected text")
                .contains(expectedMessage);
    }

    private NewProtectedResource createValidNewProtectedResource() {
        NewProtectedResource resource = new NewProtectedResource();
        resource.setName("default-name");  // Will be overridden in test
        resource.setResourceIdentifiers(List.of("https://example.com"));
        resource.setType("MCP_SERVER");
        return resource;
    }

    private UpdateProtectedResource createValidUpdateProtectedResource() {
        UpdateProtectedResource resource = new UpdateProtectedResource();
        resource.setName("default-name");  // Will be overridden in test
        resource.setResourceIdentifiers(List.of("https://example.com"));
        return resource;
    }

    /**
     * MCP Server Name Length Validation Tests
     * Following TDD approach - these tests will FAIL until @Size annotations are added to models
     */
    @Nested
    @DisplayName("MCP Server Name Length Validation")
    class ServerNameLengthValidation {

        /**
         * Unified test data: covers all operations and all length scenarios
         * Format: [operation, nameLength, expectedHttpStatus]
         */
        static Collection<Object[]> nameLengthValidationData() {
            return Arrays.asList(new Object[][]{
                    // Valid lengths - should succeed (will fail initially in TDD)
                    {Operation.CREATE, 1, HttpStatusCode.CREATED_201},
                    {Operation.CREATE, 32, HttpStatusCode.CREATED_201},
                    {Operation.CREATE, 64, HttpStatusCode.CREATED_201},  // Boundary - exactly at limit
                    {Operation.UPDATE, 1, HttpStatusCode.OK_200},
                    {Operation.UPDATE, 64, HttpStatusCode.OK_200},       // Boundary - exactly at limit
                    {Operation.PATCH, 1, HttpStatusCode.OK_200},
                    {Operation.PATCH, 64, HttpStatusCode.OK_200},        // Boundary - exactly at limit

                    // Invalid lengths - should fail (will pass initially in TDD - wrong behavior)
                    {Operation.CREATE, 65, HttpStatusCode.BAD_REQUEST_400},  // Boundary - one over
                    {Operation.CREATE, 100, HttpStatusCode.BAD_REQUEST_400},
                    {Operation.UPDATE, 65, HttpStatusCode.BAD_REQUEST_400},  // Boundary - one over
                    {Operation.PATCH, 65, HttpStatusCode.BAD_REQUEST_400},   // Boundary - one over
            });
        }

        /**
         * Single parameterized test covering ALL operations and lengths
         * Zero duplication - add new test case = add one line to data
         */
        @ParameterizedTest(name = "[{index}] {0} with name length {1} should return {2}")
        @MethodSource("nameLengthValidationData")
        void shouldValidateNameLengthForAllOperations(
                Operation operation,
                int nameLength,
                int expectedStatusCode) {

            // Arrange
            String name = "x".repeat(nameLength);

            // Act
            Response response = performOperation(operation, name);

            // Assert
            assertEquals(
                    String.format("%s with name length %d should return %d",
                            operation, nameLength, expectedStatusCode),
                    expectedStatusCode,
                    response.getStatus());

            // Additional assertion for error cases
            if (expectedStatusCode == HttpStatusCode.BAD_REQUEST_400) {
                verifyErrorMessage(response, "Name must be between 1 and 64 characters");
            }
        }

        /**
         * Test @NotBlank validation (separate concern from @Size)
         * Name is a REQUIRED field - cannot be empty or null
         */
        @Nested
        @DisplayName("Empty/Null Name Validation (@NotBlank)")
        class EmptyNullNameValidation {

            /**
             * Test data for empty/null validation
             * Format: [operation, nameValue, description]
             * Note: PATCH is tested separately due to different semantics
             */
            static Collection<Object[]> emptyNullNameData() {
                return Arrays.asList(new Object[][]{
                        {Operation.CREATE, "", "empty string"},
                        {Operation.CREATE, null, "null"},
                        {Operation.CREATE, "   ", "whitespace-only"},  // @NotBlank rejects whitespace-only
                        {Operation.UPDATE, "", "empty string"},
                        {Operation.UPDATE, null, "null"},
                        {Operation.UPDATE, "   ", "whitespace-only"},  // @NotBlank rejects whitespace-only
                });
            }

            @ParameterizedTest(name = "[{index}] {0} with {2} name should be rejected")
            @MethodSource("emptyNullNameData")
            void shouldRejectEmptyOrNullName(Operation operation, String name, String description) {
                // Act
                Response response = performOperation(operation, name);

                // Assert - Name is required, so empty/null must be rejected
                assertEquals(operation + " with " + description + " name should return 400",
                        HttpStatusCode.BAD_REQUEST_400, response.getStatus());

                // Verify error message contains validation error
                verifyErrorMessage(response, "must not be blank");
            }

            /**
             * PATCH-specific tests: Optional semantics
             */
            @Test
            @DisplayName("PATCH with null Optional should succeed (don't update name)")
            void patchWithNullOptionalShouldSucceed() {
                // null Optional = "field not provided, don't update"
                final String domainId = "domain-1";
                final Domain mockDomain = new Domain();
                mockDomain.setId(domainId);

                PatchProtectedResource resource = new PatchProtectedResource();
                resource.setName(null);  // null Optional = don't update
                resource.setDescription(Optional.of("Update description only"));

                ProtectedResource patchedResource = new ProtectedResource();
                patchedResource.setId("resource-id");
                patchedResource.setDomainId(domainId);
                patchedResource.setName("ExistingName");  // Name unchanged
                patchedResource.setResourceIdentifiers(List.of("https://example.com"));

                doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
                doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
                doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
                doReturn(Single.just(ProtectedResourcePrimaryData.of(patchedResource)))
                        .when(protectedResourceService).patch(any(Domain.class), eq("resource-id"), any(PatchProtectedResource.class), any());

                Response response = patch(target("domains")
                        .path(domainId)
                        .path("protected-resources")
                        .path("resource-id"), resource);

                assertEquals("PATCH with null Optional (don't update name) should succeed",
                        HttpStatusCode.OK_200, response.getStatus());
            }

            @Test
            @DisplayName("PATCH with Optional.of('') should be rejected (name is required)")
            void patchWithEmptyStringShouldBeRejected() {
                // Optional.of("") = "set name to empty string" → should be REJECTED
                // Name is required (@NotBlank), so we can't patch it to empty
                final String domainId = "domain-1";
                final Domain mockDomain = new Domain();
                mockDomain.setId(domainId);

                PatchProtectedResource resource = new PatchProtectedResource();
                resource.setName(Optional.of(""));  // Try to set to empty - should be rejected

                doReturn(Single.just(true)).when(permissionService).hasPermission(any(), any());
                doReturn(Flowable.empty()).when(permissionService).getReferenceIdsWithPermission(any(), any(), any(), anySet());
                doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

                Response response = patch(target("domains")
                        .path(domainId)
                        .path("protected-resources")
                        .path("resource-id"), resource);

                assertEquals("PATCH with Optional.of('') should be rejected - name is required",
                        HttpStatusCode.BAD_REQUEST_400, response.getStatus());

                verifyErrorMessage(response, "Name must be between 1 and 64 characters");
            }

        }
    }

    /**
     * MCP Server Name Pattern Validation Tests
     * Tests that names must begin with a non-whitespace character
     */
    @Nested
    @DisplayName("MCP Server Name Pattern Validation")
    class ServerNamePatternValidation {

        /**
         * Unified test data: covers all operations and pattern scenarios
         * Format: [operation, name, expectedHttpStatus, description]
         */
        static Collection<Object[]> namePatternValidationData() {
            return Arrays.asList(new Object[][]{
                    // Invalid patterns - should fail (leading whitespace)
                    // Note: whitespace-only cases are tested in @NotBlank validation (EmptyNullNameValidation)
                    {Operation.CREATE, "  My Server", HttpStatusCode.BAD_REQUEST_400, "leading spaces"},
                    {Operation.CREATE, "\tMy Server", HttpStatusCode.BAD_REQUEST_400, "leading tab"},
                    {Operation.CREATE, "\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading newline"},
                    {Operation.CREATE, " \t\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading mixed whitespace"},
                    {Operation.UPDATE, "  My Server", HttpStatusCode.BAD_REQUEST_400, "leading spaces"},
                    {Operation.UPDATE, "\tMy Server", HttpStatusCode.BAD_REQUEST_400, "leading tab"},
                    {Operation.UPDATE, "\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading newline"},
                    {Operation.UPDATE, " \t\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading mixed whitespace"},
                    {Operation.PATCH, "   ", HttpStatusCode.BAD_REQUEST_400, "whitespace-only"},
                    {Operation.PATCH, "  My Server", HttpStatusCode.BAD_REQUEST_400, "leading spaces"},
                    {Operation.PATCH, "\tMy Server", HttpStatusCode.BAD_REQUEST_400, "leading tab"},
                    {Operation.PATCH, "\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading newline"},
                    {Operation.PATCH, " \t\nMy Server", HttpStatusCode.BAD_REQUEST_400, "leading mixed whitespace"},

                    // Valid patterns - should succeed (starts with non-whitespace)
                    {Operation.CREATE, "My Server", HttpStatusCode.CREATED_201, "valid name"},
                    {Operation.CREATE, "My  Server", HttpStatusCode.CREATED_201, "valid name with spaces in middle"},
                    {Operation.CREATE, "Server123", HttpStatusCode.CREATED_201, "valid name with numbers"},
                    {Operation.CREATE, "A", HttpStatusCode.CREATED_201, "valid single character"},
                    {Operation.UPDATE, "My Server", HttpStatusCode.OK_200, "valid name"},
                    {Operation.UPDATE, "My  Server", HttpStatusCode.OK_200, "valid name with spaces in middle"},
                    {Operation.PATCH, "My Server", HttpStatusCode.OK_200, "valid name"},
                    {Operation.PATCH, "My  Server", HttpStatusCode.OK_200, "valid name with spaces in middle"},
            });
        }

        /**
         * Single parameterized test covering ALL operations and patterns
         * Zero duplication - add new test case = add one line to data
         */
        @ParameterizedTest(name = "[{index}] {0} with {3} name should return {2}")
        @MethodSource("namePatternValidationData")
        void shouldValidateNamePatternForAllOperations(
                Operation operation,
                String name,
                int expectedStatusCode,
                String description) {

            // Act
            Response response = performOperation(operation, name);

            // Assert
            assertEquals(
                    String.format("%s with %s name should return %d",
                            operation, description, expectedStatusCode),
                    expectedStatusCode,
                    response.getStatus());

            // Additional assertion for error cases
            if (expectedStatusCode == HttpStatusCode.BAD_REQUEST_400) {
                verifyErrorMessage(response, "Name must begin with a non-whitespace character");
            }
        }
    }

}