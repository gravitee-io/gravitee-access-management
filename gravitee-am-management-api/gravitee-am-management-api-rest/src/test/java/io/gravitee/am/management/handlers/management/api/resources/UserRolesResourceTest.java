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
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserRolesResourceTest extends JerseySpringTest {

    private static Set<Role> roles = new HashSet<>();

    private User mockUser;
    private Domain mockDomain;
    private String domainId;

    @Before
    public void before(){
        domainId = "domain-1";
        mockDomain = new Domain();
        mockDomain.setId(domainId);

        mockUser = new User();
        mockUser.setId("user-id-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockUser)).when(userService).findById(mockUser.getId());

        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setClientId("clientId");
        scopeApproval.setScope("scope");
        scopeApproval.setDomain(domainId);
    }

    @Test
    public void shouldGetUserRoles() {
        mockUser.setRoles(Collections.singletonList("role-1"));
        doReturn(Single.just(Collections.singleton("role-1"))).when(roleService).findByIdIn(mockUser.getRoles());
        doReturn(Single.just(List.of())).when(roleService).findByIdIn(mockUser.getDynamicRoles());

        final Response response = getResponse(domainId, mockUser.getId());

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 1);
    }

    @Test
    public void shouldGetUserRoles_dynamic() {
        mockUser.setDynamicRoles(Collections.singletonList("role-1"));
        doReturn(Single.just(Collections.singleton("role-1"))).when(roleService).findByIdIn(mockUser.getDynamicRoles());
        doReturn(Single.just(List.of())).when(roleService).findByIdIn(mockUser.getRoles());

        final Response response = getResponse(domainId, mockUser.getId(), true);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 1);
    }

    @Test
    public void shouldGetUserRoles_emptyDynamic() {
        mockUser.setRoles(Collections.singletonList("role-1"));
        doReturn(Single.just(Collections.singleton("role-1"))).when(roleService).findByIdIn(mockUser.getRoles());
        doReturn(Single.just(List.of())).when(roleService).findByIdIn(mockUser.getDynamicRoles());

        final Response response = getResponse(domainId, mockUser.getId(), true);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_emptyRoles() {
        mockUser.setDynamicRoles(Collections.singletonList("role-1"));
        doReturn(Single.just(Collections.singleton("role-1"))).when(roleService).findByIdIn(mockUser.getRoles());
        doReturn(Single.just(List.of())).when(roleService).findByIdIn(mockUser.getRoles());

        final Response response = getResponse(domainId, mockUser.getId(), false);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_emptyBoth() {
        mockUser.setRoles(List.of());
        mockUser.setDynamicRoles(List.of());
        final Response response = getResponse(domainId, mockUser.getId(), false);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_dynamicEmptyBoth() {
        mockUser.setRoles(List.of());
        mockUser.setDynamicRoles(List.of());
        final Response response = getResponse(domainId, mockUser.getId(), true);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_nullBoth() {
        final Response response = getResponse(domainId, mockUser.getId(), false);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_dynamicNullBoth() {
        final Response response = getResponse(domainId, mockUser.getId(), true);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(response.readEntity(roles.getClass()).size(), 0);
    }

    @Test
    public void shouldGetUserRoles_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = getResponse(domainId, "user1");
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    private Response getResponse(String domainId, String id) {
        return getResponse(domainId, id, false);
    }

    private Response getResponse(String domainId, String id, boolean isDynamic) {

        return target("domains")
                .path(domainId)
                .path("users")
                .path(id)
                .path("roles")
                .queryParam("dynamic", isDynamic)
                .request()
                .get();
    }
}
