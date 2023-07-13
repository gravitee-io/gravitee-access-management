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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RolesResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetRoles() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Role mockRole = new Role();
        mockRole.setId("role-1-id");
        mockRole.setName("role-1-name");
        mockRole.setReferenceId(domainId);

        final Role mockRole2 = new Role();
        mockRole2.setId("role-2-id");
        mockRole2.setName("role-2-name");
        mockRole2.setReferenceId(domainId);

        final Set<Role> roles = new HashSet<>(Arrays.asList(mockRole, mockRole2));
        final Page<Role> pagedRoles = new Page<>(roles, 0, 2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(pagedRoles)).when(roleService).findByDomain(domainId, 0, 50);

        final Response response = target("domains").path(domainId).path("roles").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        JsonArray pageArray = new JsonObject(readEntity(response, String.class)).getJsonArray("data");
        assertTrue(pageArray.size() == 2);
    }

    @Test
    public void shouldSearchRoles() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Role mockRole = new Role();
        mockRole.setId("role-1-id");
        mockRole.setName("role-1-name");
        mockRole.setReferenceId(domainId);

        final Role mockRole2 = new Role();
        mockRole2.setId("role-2-id");
        mockRole2.setName("role-2-name");
        mockRole2.setReferenceId(domainId);

        final Set<Role> roles = new HashSet<>(Arrays.asList(mockRole, mockRole2));
        final Page<Role> pagedRoles = new Page<>(roles, 0, 2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(roles)).when(roleService).findByDomain(domainId);
        doReturn(Single.just(pagedRoles)).when(roleService).searchByDomain(domainId, "*role-2-name*", 0, 50);

        final Response response = target("domains").path(domainId).path("roles").queryParam("q", "*role-2-name*").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        JsonArray pageArray = new JsonObject(readEntity(response, String.class)).getJsonArray("data");
        assertTrue(pageArray.size() == 2);
    }

    @Test
    public void shouldGetRoles_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(roleService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("roles").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewRole newRole = new NewRole();
        newRole.setName("role-name");

        Role role = new Role();
        role.setId("role-id");
        role.setName("role-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(role)).when(roleService).create(eq(domainId), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("roles")
                .request().post(Entity.json(newRole));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
