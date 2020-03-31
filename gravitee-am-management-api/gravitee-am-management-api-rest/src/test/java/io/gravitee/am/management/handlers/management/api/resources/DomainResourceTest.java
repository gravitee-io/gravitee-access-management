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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);
        mockDomain.setName("domain-name");

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals("domain-name", domain.getName());
    }

    @Test
    public void shouldGetDomain_notFound() {
        final String domainId = "domain-id";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetDomain_technicalManagementException() {
        final String domainId = "domain-id";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
