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
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class ProtectedResourceResourceTest extends JerseySpringTest {

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
                .when(protectedResourceService).findByDomainAndIdAndType(eq(domainId), eq("id"), eq(ProtectedResource.Type.MCP_SERVER));


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        Map data = response.readEntity(Map.class);
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
                .when(protectedResourceService).findByDomainAndIdAndType(eq(domainId), eq("id"), eq(ProtectedResource.Type.MCP_SERVER));


        final Response response = target("domains")
                .path(domainId)
                .path("protected-resources")
                .path("id")
                .queryParam("type", "MCP_SERVER")
                .request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

}