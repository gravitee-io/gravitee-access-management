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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.ApplicationFilter;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Tests for the status and owner.email filter parameters on the application list endpoint.
 */
public class ApplicationsResourceFilterTest extends JerseySpringTest {

    @BeforeEach
    public void setUpPermissionStubs() {
        // getReferenceIdsWithPermission is evaluated eagerly during Rx chain construction;
        // stub it to prevent NPE even when the switchIfEmpty branch is not subscribed to.
        doReturn(Flowable.empty()).when(permissionService)
                .getReferenceIdsWithPermission(any(), any(), any(), any());
        // Shared Spring mock beans accumulate invocations across tests; clear before each test
        // so verify() counts only the invocations from the current test.
        Mockito.clearInvocations(applicationService);
    }

    @Test
    public void shouldGetApps_withStatusFilter_callsFilteredService() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockClient = new Application();
        mockClient.setId("client-1-id");
        mockClient.setName("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Page<Application> applicationPage = new Page<>(List.of(mockClient), 0, 1);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(applicationPage)).when(applicationService)
                .findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));

        final Response response = target("domains").path(domainId).path("applications")
                .queryParam("status", "enabled")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(1, ((List) ((Map) readEntity(response, Map.class)).get("data")).size());
        Mockito.verify(applicationService).findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));
        Mockito.verify(applicationService, Mockito.never()).findByDomain(domainId, 0, 50);
    }

    @Test
    public void shouldGetApps_withOwnerEmailFilter_callsFilteredService() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockClient = new Application();
        mockClient.setId("client-1-id");
        mockClient.setName("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Page<Application> applicationPage = new Page<>(List.of(mockClient), 0, 1);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(applicationPage)).when(applicationService)
                .findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));

        final Response response = target("domains").path(domainId).path("applications")
                .queryParam("owner.email", "owner@example.com")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(1, ((List) ((Map) readEntity(response, Map.class)).get("data")).size());
        Mockito.verify(applicationService).findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));
        Mockito.verify(applicationService, Mockito.never()).findByDomain(domainId, 0, 50);
    }

    @Test
    public void shouldGetApps_withOwnerEmailFilter_forbidden_whenNoOrgUserReadPermission() {
        final String domainId = "domain-1";

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(domainId).path("applications")
                .queryParam("owner.email", "owner@example.com")
                .request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldGetApps_withStatusAndOwnerEmailFilter_callsFilteredService() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockClient = new Application();
        mockClient.setId("client-1-id");
        mockClient.setName("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Page<Application> applicationPage = new Page<>(List.of(mockClient), 0, 1);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(applicationPage)).when(applicationService)
                .findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));

        final Response response = target("domains").path(domainId).path("applications")
                .queryParam("status", "enabled")
                .queryParam("owner.email", "owner@example.com")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(1, ((List) ((Map) readEntity(response, Map.class)).get("data")).size());
        Mockito.verify(applicationService).findByDomain(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq(0), eq(50));
    }

    @Test
    public void shouldGetApps_withStatusFilterAndQuery_callsFilteredSearch() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockClient = new Application();
        mockClient.setId("client-1-id");
        mockClient.setName("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Page<Application> applicationPage = new Page<>(List.of(mockClient), 0, 1);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(applicationPage)).when(applicationService)
                .search(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq("myapp"), eq(0), eq(50));

        final Response response = target("domains").path(domainId).path("applications")
                .queryParam("status", "enabled")
                .queryParam("q", "myapp")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(1, ((List) ((Map) readEntity(response, Map.class)).get("data")).size());
        Mockito.verify(applicationService).search(eq(domainId), eq("DEFAULT"), any(ApplicationFilter.class), eq("myapp"), eq(0), eq(50));
        Mockito.verify(applicationService, Mockito.never()).search(domainId, "myapp", 0, 50);
    }
}
