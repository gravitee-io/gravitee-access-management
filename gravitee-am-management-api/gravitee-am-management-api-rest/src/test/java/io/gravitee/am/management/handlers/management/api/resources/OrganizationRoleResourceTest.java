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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Role;
import io.gravitee.am.service.exception.ResourceForbiddenAccessException;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class OrganizationRoleResourceTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "DEFAULT";

    @Test
    public void shouldNotUpdateRole_permissionsTheCallerDoesNotHold() {
        final List<String> permissions = List.of("organization_user_delete");
        doReturn(Completable.error(new ResourceForbiddenAccessException("Unable to grant permissions you do not hold: organization_user_delete")))
                .when(permissionService).checkCanGrant(any(), eq(ORGANIZATION_ID), eq(Reference.organization(ORGANIZATION_ID)), eq(permissions));
        doReturn(Single.just(new Role())).when(roleService).update(any(), eq(ORGANIZATION_ID), eq("role-id"), any(UpdateRole.class), any());

        final Response response = target("organizations").path(ORGANIZATION_ID).path("roles").path("role-id")
                .request().put(Entity.json(Map.of("name", "ORGANIZATION_USER", "permissions", permissions)));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }
}
