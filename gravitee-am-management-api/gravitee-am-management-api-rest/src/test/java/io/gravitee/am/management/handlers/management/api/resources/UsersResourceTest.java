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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.Maps;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsersResourceTest extends JerseySpringTest {

    public static final String ORGANIZATION_DEFAULT = "DEFAULT";

    @Before
    public void setUp() {
        doReturn(Completable.complete()).when(userValidator).validate(any());
    }

    @Test
    public void shouldGetUsers() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final User mockUser = new User();
        mockUser.setId("user-id-1");
        mockUser.setUsername("username-1");
        mockUser.setReferenceType(ReferenceType.DOMAIN);
        mockUser.setReferenceId(domainId);

        final User mockUser2 = new User();
        mockUser2.setId("domain-id-2");
        mockUser2.setUsername("username-2");
        mockUser2.setReferenceType(ReferenceType.DOMAIN);
        mockUser2.setReferenceId(domainId);

        final Set<User> users = new HashSet<>(Arrays.asList(mockUser, mockUser2));
        final Page<User> pagedUsers = new Page<>(users, 0, 2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(pagedUsers)).when(userService).findAll(ReferenceType.DOMAIN, domainId, 0, 10);

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetOrganizationUsers() {
        final String organizationId = "DEFAULT";

        final User mockUser = new User();
        mockUser.setId("user-id-1");
        mockUser.setUsername("username-1");
        mockUser.setPassword("SomePassWord-1");
        mockUser.setReferenceType(ORGANIZATION);
        mockUser.setReferenceId(organizationId);

        final User mockUser2 = new User();
        mockUser2.setId("domain-id-2");
        mockUser2.setUsername("username-2");
        mockUser2.setPassword("SomePassWord-2");
        mockUser2.setReferenceType(ORGANIZATION);
        mockUser2.setReferenceId(organizationId);

        final Set<User> users = new HashSet<>(Arrays.asList(mockUser, mockUser2));
        final Page<User> pagedUsers = new Page<>(users, 0, 2);

        final Map<Permission, Set<Acl>> permissions = Maps.<Permission, Set<Acl>>builder().put(Permission.ORGANIZATION_USER, Sets.newHashSet(Acl.LIST)).build();
        when(permissionService.findAllPermissions(any(), eq(ORGANIZATION), eq(organizationId))).thenReturn(Single.just(permissions));
        doReturn(Single.just(pagedUsers)).when(organizationUserService).findAll(ORGANIZATION, organizationId, 0, 10);

        final Response response = target("organizations")
                .path("DEFAULT")
                .path("users")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        Page<User> values = readEntity(response, new TypeReference<>() {
        });

        assertEquals(values.getCurrentPage(), 0);
        assertEquals(values.getTotalCount(), 2);
        final Collection<User> data = values.getData();

        assertTrue(getFilteredElements(data, User::getId).containsAll(List.of("user-id-1", "domain-id-2")));
        assertTrue(getFilteredElements(data, User::getUsername).containsAll(List.of("username-1", "username-2")));
        assertTrue(getFilteredElements(data, User::getPassword).isEmpty());
    }

    private static <T> List<T> getFilteredElements(Collection<User> data, Function<User, T> mapper) {
        return data.stream().map(mapper).filter(Objects::nonNull).distinct().collect(toList());
    }

    @Test
    public void shouldNotSearchUsers_invalidFilter() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .queryParam("filter", "invalid-filter")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .request()
                .get();

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldGetUsers_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("users").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldNotCreate_invalid_identity_provider() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setPassword("password");
        newUser.setEmail("test@test.com");
        newUser.setSource("unknown-source");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new UserProviderNotFoundException(newUser.getSource()))).when(userService).create(any(Domain.class), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .request().post(Entity.json(newUser));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
    @Test
    public void shouldCreateOrganizationUser() {
        when(permissionService.hasPermission(any(), any())).thenReturn(Single.just(true));
        when(organizationService.findById(ORGANIZATION_DEFAULT)).thenReturn(Single.just(new Organization()));

        final User mockUser = new User();
        mockUser.setId("user-id-1");
        mockUser.setUsername("username-1");
        mockUser.setPassword("SomePassWord-1");
        mockUser.setReferenceType(ORGANIZATION);
        mockUser.setReferenceId("DEFAULT");

        when(organizationUserService.createGraviteeUser(any(), any(), any())).thenReturn(Single.just(mockUser));

        final NewUser entity = new NewUser();
        entity.setUsername("test");
        entity.setPassword("password");
        entity.setEmail("email@acme.fr");
        final Response response = target("organizations")
                .path(ORGANIZATION_DEFAULT)
                .path("users")
                .request()
                .post(Entity.entity(entity, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        verify(organizationUserService).createGraviteeUser(any(), any(), any());

        User user = readEntity(response, User.class);
        assertEquals(user.getId(), mockUser.getId());
        assertEquals(user.getUsername(), mockUser.getUsername());
        assertNull(user.getPassword());
        assertEquals(user.getReferenceId(), mockUser.getReferenceId());
    }
}
