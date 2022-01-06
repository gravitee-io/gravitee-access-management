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
import io.gravitee.am.management.handlers.management.api.model.PasswordValue;
import io.gravitee.am.management.handlers.management.api.model.StatusEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetUser() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "user-id";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("user-username");
        mockUser.setReferenceType(ReferenceType.DOMAIN);
        mockUser.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockUser)).when(userService).findById(userId);

        final Response response = target("domains").path(domainId).path("users").path(userId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final User user = readEntity(response, User.class);
        assertEquals(domainId, user.getReferenceId());
        assertEquals("user-username", user.getUsername());
    }

    @Test
    public void shouldGetUser_notFound() {
        final String domainId = "domain-id";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains/" + domainId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetUser_technicalManagementException() {
        final String domainId = "domain-id";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldNotLockUser_domainNotFound() {
        final String domainId = "domain-id";
        final String userId = "userId";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains/" + domainId + "/users/" + userId + "/lock").request().post(Entity.json(null));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldLockUser() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).lock(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("lock").request().post(Entity.json(null));
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotUnlockUser_domainNotFound() {
        final String domainId = "domain-id";
        final String userId = "userId";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("users").path(userId).path("unlock").request().post(Entity.json(null));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUnlockUser() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).unlock(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("unlock").request().post(Entity.json(null));
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotResetPassword_domainNotFound() {
        final String domainId = "domain-id";
        final String userId = "userId";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final PasswordValue entity = new PasswordValue();
        entity.setPassword("aPassword");
        final Response response = target("domains").path(domainId).path("users").path(userId).path("resetPassword").request().post(Entity.json(entity));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotResetPassword_bad_payload() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        final String somePassword = "somePassword";
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).resetPassword(eq(mockDomain), eq(userId), eq(somePassword), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("resetPassword")
                .request()
                .post(Entity.json(null));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldNotResetPassword_bad_payload_null_password() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        final String somePassword = "somePassword";
        var passwordValue = new PasswordValue();
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).resetPassword(eq(mockDomain), eq(userId), eq(somePassword), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("resetPassword")
                .request()
                .post(Entity.json(passwordValue));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldResetPassword() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        var passwordValue = new PasswordValue();
        passwordValue.setPassword("somePassword");
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).resetPassword(eq(mockDomain), eq(userId), eq(passwordValue.getPassword()), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("resetPassword")
                .request()
                .post(Entity.json(passwordValue));
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldSendRegistrationConfirmation() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).sendRegistrationConfirmation(eq(domainId), eq(userId), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("sendRegistrationConfirmation").request().post(Entity.json(null));
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotDeleteUser_domainNotFound() {
        final String domainId = "domain-id";
        final String userId = "userId";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("users").path(userId).request().delete();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteUser() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(userService).delete(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).request().delete();
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldUpdateStatus() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("user-username");
        mockUser.setReferenceType(ReferenceType.DOMAIN);
        mockUser.setReferenceId(domainId);
        mockUser.setEnabled(false);

        var statusEntity = new StatusEntity();
        statusEntity.setEnabled(false);
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockUser)).when(userService).updateStatus(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), eq(statusEntity.isEnabled()), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).path("status").request().put(Entity.json(statusEntity));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final User user = readEntity(response, User.class);
        assertEquals(domainId, user.getReferenceId());
        assertEquals(statusEntity.isEnabled(), user.isEnabled());
    }

    @Test
    public void shouldNotUpdateUser_domainNotFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("user-username");
        mockUser.setReferenceType(ReferenceType.DOMAIN);
        mockUser.setReferenceId(domainId);
        mockUser.setEnabled(false);

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final UpdateUser entity = new UpdateUser();
        entity.setEmail("email@email.com");
        entity.setFirstName("firstname");

        final Response response = target("domains").path(domainId).path("users").path(userId).request().put(Entity.json(entity));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateUser() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String userId = "userId";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("username");
        mockUser.setFirstName("firstname");
        mockUser.setReferenceType(ReferenceType.DOMAIN);
        mockUser.setReferenceId(domainId);
        mockUser.setEnabled(false);

        final UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("email@email.com");
        updateUser.setFirstName("firstname");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockUser)).when(userService).update(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), any(), any());

        final Response response = target("domains").path(domainId).path("users").path(userId).request().put(Entity.json(updateUser));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final User user = readEntity(response, User.class);
        assertEquals(domainId, user.getReferenceId());
        assertEquals("firstname", user.getFirstName());
    }

}
