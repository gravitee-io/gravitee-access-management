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
package io.gravitee.am.management.handlers.management.api.resources.platform.roles;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.RoleEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SystemRoleResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetSystemRole() {

        final String roleId = "role-id";
        final Role mockRole = new Role();
        mockRole.setId(roleId);
        mockRole.setName("role-name");

        doReturn(Single.just(mockRole)).when(roleService).findById(ReferenceType.PLATFORM, Platform.DEFAULT, roleId);

        final Response response = target("platform").path("roles").path(roleId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final RoleEntity role = readEntity(response, RoleEntity.class);
        assertEquals(roleId, role.getId());
    }

    @Test
    public void shouldGetRole_notFound() {

        final String roleId = "role-id";

        doReturn(Single.error(new RoleNotFoundException(roleId))).when(roleService).findById(ReferenceType.PLATFORM, Platform.DEFAULT, roleId);

        final Response response = target("platform").path("roles").path(roleId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
