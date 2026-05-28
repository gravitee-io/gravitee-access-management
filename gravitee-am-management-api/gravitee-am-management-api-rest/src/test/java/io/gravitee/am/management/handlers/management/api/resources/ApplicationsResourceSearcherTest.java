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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiRequest;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class ApplicationsResourceSearcherTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "DEFAULT";
    private static final String DOMAIN_ID = "domain-1";

    @BeforeEach
    public void stubFallbackIds() {
        doReturn(Flowable.empty())
                .when(permissionService).getReferenceIdsWithPermission(any(), eq(ReferenceType.APPLICATION), eq(Permission.APPLICATION), eq(Set.of(Acl.READ)));
    }

    private Domain mockDomain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    private Application sampleApp(String id, String name) {
        final Application app = new Application();
        app.setId(id);
        app.setName(name);
        app.setDomain(DOMAIN_ID);
        app.setUpdatedAt(new Date());
        return app;
    }

    private Application sampleAppWithClientId(String id, String name, String clientId) {
        final Application app = sampleApp(id, name);
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId(clientId);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private CursorPage<Application, ApplicationCursorRequest> page(List<Application> data,
                                                                   ApplicationCursorRequest nextCursor,
                                                                   Long totalCount) {
        return new CursorPage<>(data, nextCursor, totalCount);
    }

    @Test
    public void shouldSearch_returnsApplications() {
        final Application app1 = sampleApp("app-1", "app-1-name");
        final Application app2 = sampleApp("app-2", "app-2-name");
        final CursorPage<Application, ApplicationCursorRequest> result = page(List.of(app1, app2), null, 2L);

        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(result))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        assertEquals(2, ((List) responseEntity.get("data")).size());
        assertEquals(2, ((Number) responseEntity.get("totalCount")).intValue());
        assertEquals(0, ((Number) responseEntity.get("page")).intValue());
        assertNull(responseEntity.get("nextCursor"));
    }

    @Test
    public void shouldSearch_withClientIdExpand_returnsClientId() {
        final Application app = sampleAppWithClientId("app-1", "app-1-name", "oauth-client-id");
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(app), null, 1L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("expand", "clientId").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        final Map firstApp = (Map) ((List) responseEntity.get("data")).get(0);
        assertEquals("oauth-client-id", firstApp.get("clientId"));
    }

    @Test
    public void shouldSearch_withoutExpand_omitsClientId() {
        final Application app = sampleAppWithClientId("app-1", "app-1-name", "oauth-client-id");
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(app), null, 1L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        final Map firstApp = (Map) ((List) responseEntity.get("data")).get(0);
        assertNull(firstApp.get("clientId"));
    }

    @Test
    public void shouldSearch_statusEnabled_setsEnabledTrue() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("status", "enabled").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        assertEquals(Boolean.TRUE, captor.getValue().getEnabled());
    }

    @Test
    public void shouldSearch_statusDisabled_setsEnabledFalse() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("status", "disabled").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        assertEquals(Boolean.FALSE, captor.getValue().getEnabled());
    }

    @Test
    public void shouldSearch_noStatusParam_leavesEnabledNull() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        assertNull(captor.getValue().getEnabled());
    }

    @Test
    public void shouldSearch_typesAreForwarded() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("type", "WEB").queryParam("type", "SERVICE").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        assertEquals(List.of(ApplicationType.WEB, ApplicationType.SERVICE), captor.getValue().getTypes());
    }

    @Test
    public void shouldSearch_queryAndOwnerEmailForwarded() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("q", "alpha*").queryParam("owner.email", "owner@example.com").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> requestCaptor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        final ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), requestCaptor.capture(), ownerCaptor.capture(), anyInt());
        assertEquals("alpha*", requestCaptor.getValue().getQuery());
        assertEquals("owner@example.com", ownerCaptor.getValue());
    }

    @Test
    public void shouldSearch_defaultsAreInitialCursor() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        final ApplicationCursorRequest req = captor.getValue();
        assertTrue("expected initial cursor", req.isFirstPage());
        assertEquals("updatedAt", req.getSortField());
        assertEquals(CursorRequest.SortDirection.DESC, req.getSortDirection());
        assertEquals(0, req.getPage());
    }

    @Test
    public void shouldSearch_nextCursor_returnsPathInResponse() {
        final ApplicationCursorRequest nextCursor = new ApplicationCursorRequest(
                "value-50",
                "app-50",
                CursorRequest.SortDirection.DESC,
                "updatedAt",
                1,
                "alpha*",
                Boolean.TRUE,
                List.of(ApplicationType.WEB));
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), nextCursor, 200L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("q", "alpha*")
                .queryParam("status", "enabled")
                .queryParam("type", "WEB")
                .queryParam("owner.email", "owner@example.com")
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        final String path = (String) responseEntity.get("nextCursor");
        assertNotNull("nextCursor should be present when paging is incomplete", path);
        assertTrue(path.startsWith("/organizations/" + ORGANIZATION_ID + "/environments/DEFAULT/domains/" + DOMAIN_ID + "/applications/search/_cursor?"));
        assertTrue("expected encoded cursor in nextCursor: " + path,
                path.contains("cursor=" + new CursorApiRequest("app-50", "value-50").encode()));
        assertTrue("expected page=1 in nextCursor: " + path, path.contains("page=1"));
        assertTrue("expected q=alpha* in nextCursor: " + path, path.contains("q=alpha*"));
        assertTrue("expected status=enabled in nextCursor: " + path, path.contains("status=enabled"));
        assertTrue("expected type=WEB in nextCursor: " + path, path.contains("type=WEB"));
    }

    @Test
    public void shouldSearch_serviceTechnicalError_returns500() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new TechnicalManagementException("boom")))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldSearch_serviceIllegalArgument_returns400() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new IllegalArgumentException("invalid sort field")))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldSearchCursor_buildsCursorFromQueryParams() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search").path("_cursor")
                .queryParam("sort", "name")
                .queryParam("dir", "ASC")
                .queryParam("page", "3")
                .queryParam("cursor", new CursorApiRequest("app-42", "v-42").encode())
                .queryParam("q", "foo")
                .queryParam("status", "enabled")
                .queryParam("type", "AGENT")
                .request().get();

        final ArgumentCaptor<ApplicationCursorRequest> captor = ArgumentCaptor.forClass(ApplicationCursorRequest.class);
        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), captor.capture(), any(), anyInt());
        final ApplicationCursorRequest req = captor.getValue();
        assertEquals("name", req.getSortField());
        assertEquals(CursorRequest.SortDirection.ASC, req.getSortDirection());
        assertEquals(3, req.getPage());
        assertEquals("v-42", req.getLastSortValue());
        assertEquals("app-42", req.getLastId());
        assertEquals("foo", req.getQuery());
        assertEquals(Boolean.TRUE, req.getEnabled());
        assertEquals(List.of(ApplicationType.AGENT), req.getTypes());
    }

    @Test
    public void shouldSearchCursor_returnsApplications() {
        final Application app = sampleApp("app-1", "app-1-name");
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(app), null, 1L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").path("_cursor")
                .queryParam("cursor", new CursorApiRequest("app-1", "val-1").encode()).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        assertEquals(1, ((List) responseEntity.get("data")).size());
    }

    @Test
    public void shouldSearch_limitParamPassedThrough() {
        doReturn(Maybe.just(mockDomain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(page(List.of(), null, 0L)))
                .when(applicationSearcher).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), anyInt());

        target("domains").path(DOMAIN_ID).path("applications").path("search")
                .queryParam("limit", "10").request().get();

        verify(applicationSearcher, atLeastOnce()).searchByDomainCursor(eq(ORGANIZATION_ID), eq(DOMAIN_ID), any(ApplicationCursorRequest.class), any(), eq(10));
    }

    @Test
    public void shouldSearch_domainNotFound_returns404() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("applications").path("search").request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
