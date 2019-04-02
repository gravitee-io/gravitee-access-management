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
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsersResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetUsers() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final User mockUser = new User();
        mockUser.setId("user-id-1");
        mockUser.setUsername("username-1");
        mockUser.setDomain(domainId);

        final User mockUser2 = new User();
        mockUser2.setId("domain-id-2");
        mockUser2.setUsername("username-2");
        mockUser2.setDomain(domainId);

        final Set<User> users = new HashSet(Arrays.asList(mockUser, mockUser2));
        final Page<User> pagedUsers = new Page<>(users, 0, 2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(pagedUsers)).when(userService).findByDomain(domainId, 0, 10);

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
    public void shouldGetUsers_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("users").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldNotCreate_invalid_password() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setPassword("password");
        newUser.setEmail("test@test.com");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(false).when(passwordValidator).validate(anyString());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .request().post(Entity.json(newUser));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
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
        doReturn(Single.error(new UserProviderNotFoundException(newUser.getSource()))).when(userService).create(anyString(), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .request().post(Entity.json(newUser));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setPassword("password");
        newUser.setEmail("test@test.com");

        doReturn(true).when(passwordValidator).validate(anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new User())).when(userService).create(anyString(), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .request().post(Entity.json(newUser));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
