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
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.ResourceForbiddenAccessException;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * The actions on the users of the organization are refused when the target is more privileged than the caller.
 */
public class OrganizationUserResourceTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "DEFAULT";
    private static final String ADMIN_ID = "admin-id";

    private Organization organization() {
        final Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        return organization;
    }

    private void callerCannotManage(String userId) {
        doReturn(Completable.error(new ResourceForbiddenAccessException("Unable to manage a user with more privileges than yours")))
                .when(permissionService).checkCanManageUser(any(), eq(ORGANIZATION_ID), eq(userId));
    }

    @Test
    public void shouldNotResetPassword_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Single.just(new User())).when(organizationUserService).findById(any(), eq(ORGANIZATION_ID), eq(ADMIN_ID));

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID).path("resetPassword")
                .request().post(Entity.json(Map.of("password", "Password#123456")));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotUpdateUser_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Single.just(new User())).when(organizationUserService).update(any(), eq(ORGANIZATION_ID), eq(ADMIN_ID), any(UpdateUser.class), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID)
                .request().put(Entity.json(Map.of("externalId", "attacker-controlled")));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotUpdateUserStatus_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Single.just(new User())).when(organizationUserService).updateStatus(eq(ORGANIZATION_ID), eq(ADMIN_ID), eq(false), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID).path("status")
                .request().put(Entity.json(Map.of("enabled", false)));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotCreateAccountToken_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Single.never()).when(organizationUserService).createAccountAccessToken(eq(ORGANIZATION_ID), eq(ADMIN_ID), any(NewAccountAccessToken.class), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID).path("tokens")
                .request().post(Entity.json(Map.of("name", "x")));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotRevokeAccountToken_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Maybe.never()).when(organizationUserService).revokeToken(eq(ORGANIZATION_ID), eq(ADMIN_ID), eq("token-id"), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID).path("tokens").path("token-id")
                .request().delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotDeleteUser_targetMorePrivileged() {
        callerCannotManage(ADMIN_ID);
        doReturn(Single.never()).when(organizationUserService).delete(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), eq(ADMIN_ID), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path(ADMIN_ID)
                .request().delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldNotBulkDeleteUser_targetMorePrivileged() {
        final AtomicBoolean executed = new AtomicBoolean(false);
        callerCannotManage(ADMIN_ID);
        doReturn(Single.fromCallable(() -> {
            executed.set(true);
            return new User();
        })).when(organizationUserService).delete(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), eq(ADMIN_ID), any());
        doReturn(Single.just(organization())).when(organizationService).findById(ORGANIZATION_ID);

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path("bulk")
                .request().post(Entity.json(Map.of("action", "DELETE", "items", List.of(ADMIN_ID))));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertFalse(executed.get());
    }

    @Test
    public void shouldNotBulkUpdateUser_targetMorePrivileged() {
        final AtomicBoolean executed = new AtomicBoolean(false);
        callerCannotManage(ADMIN_ID);
        doReturn(Single.fromCallable(() -> {
            executed.set(true);
            return new User();
        })).when(organizationUserService).update(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), eq(ADMIN_ID), any(UpdateUser.class), any());
        doReturn(Single.just(organization())).when(organizationService).findById(ORGANIZATION_ID);

        final Response response = target("organizations").path(ORGANIZATION_ID).path("users").path("bulk")
                .request().post(Entity.json(Map.of("action", "UPDATE", "items", List.of(Map.of("id", ADMIN_ID, "firstName", "x")))));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertFalse(executed.get());
    }
}
