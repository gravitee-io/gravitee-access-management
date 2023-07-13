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
import io.gravitee.am.management.handlers.management.api.model.UsernameEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static jakarta.ws.rs.HttpMethod.PATCH;
import static org.glassfish.jersey.client.HttpUrlConnectorProvider.SET_METHOD_WORKAROUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
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
        mockUser.setSource("source");
        doReturn(Maybe.empty()).when(identityProviderService).findById(any());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockUser)).when(userService).findById(userId);

        final Response response = target("domains").path(domainId).path("users").path(userId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map user = readEntity(response, HashMap.class);
        assertEquals(domainId, user.get("referenceId"));
        assertEquals(mockUser.getUsername(), user.get("username"));
        assertEquals(mockUser.getSource(), user.get("sourceId"));
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
        doReturn(Completable.complete()).when(userActivityService).deleteByDomainAndUser(eq(domainId), eq(userId));

        final Response response = target("domains").path(domainId).path("users").path(userId).request().delete();
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldUpdateUsername() {
        final var domainId = "domain-id";
        final var domain = new Domain();
        domain.setId(domainId);

        final var userId = "userId";
        final var userToUpdate = new User();
        userToUpdate.setId(userId);
        final var username = "user-username";
        userToUpdate.setUsername(username);

        var usernameEntity = new UsernameEntity();
        usernameEntity.setUsername(username);
        doReturn(Maybe.just(domain)).when(domainService).findById(domainId);
        doReturn(Single.just(userToUpdate)).when(userService).updateUsername(eq(ReferenceType.DOMAIN), eq(domainId), eq(userId), eq(usernameEntity.getUsername()), any());

        final var response = target("domains").path(domainId).path("users").path(userId).path("username").request()
                                              .property(SET_METHOD_WORKAROUND, true)
                                              .method(PATCH, Entity.json(usernameEntity));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final var updatedUser = readEntity(response, User.class);
        assertEquals(usernameEntity.getUsername(), updatedUser.getUsername());
    }

    @Test
    public void shouldNotUpdateUsername_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById("domainId");
        var usernameEntity = new UsernameEntity();
        usernameEntity.setUsername("username");
        var response = target("domains").path("domainId").path("users").path("userId").path("username").request()
                                        .property(SET_METHOD_WORKAROUND, true)
                                        .method(PATCH, Entity.json(usernameEntity));
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
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
    public void shouldUpdateStatus_organization() {
        final String referenceId = "DEFAULT";

        final String userId = "userId";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("user-username");
        mockUser.setPassword("password");
        mockUser.setReferenceType(ReferenceType.ORGANIZATION);
        mockUser.setReferenceId(referenceId);
        mockUser.setEnabled(false);

        var statusEntity = new StatusEntity();
        statusEntity.setEnabled(false);
        doReturn(Single.just(mockUser)).when(organizationUserService)
            .updateStatus(eq(ReferenceType.ORGANIZATION), eq(referenceId), eq(userId), eq(statusEntity.isEnabled()), any());

        final Response response = target("organizations").path(referenceId).path("users").path(userId).path("status").request().put(Entity.json(statusEntity));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final User user = readEntity(response, User.class);
        assertEquals(referenceId, user.getReferenceId());
        assertEquals(userId, user.getId());
        assertEquals(mockUser.getUsername(), user.getUsername());
        assertNull(user.getPassword());
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

    @Test
    public void shouldUpdateUser_organization() {
        final String organization = "DEFAULT";
        final Domain mockDomain = new Domain();
        mockDomain.setId(organization);

        final String userId = "userId";
        final User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("username");
        mockUser.setFirstName("firstname");
        mockUser.setReferenceType(ReferenceType.ORGANIZATION);
        mockUser.setReferenceId(organization);
        mockUser.setEnabled(true);

        final UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("email@email.com");
        updateUser.setFirstName("firstname");

        doReturn(Single.just(mockUser)).when(organizationUserService).update(eq(ReferenceType.ORGANIZATION), eq(organization), eq(userId), any(), any());

        final Response response = target("organizations")
            .path(organization)
            .path("users")
            .path(userId)
            .request()
            .put(Entity.json(updateUser));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final User user = readEntity(response, User.class);
        assertEquals(organization, user.getReferenceId());
        assertEquals("username", user.getUsername());
        assertEquals("firstname", user.getFirstName());
        assertEquals("userId", user.getId());
        assertNull(user.getPassword());
    }

}
