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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
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
public class ApplicationResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetApp() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Application mockClient = new Application();
        mockClient.setId(clientId);
        mockClient.setName("client-name");
        mockClient.setDomain(domainId);

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockClient)).when(applicationService).findById(clientId);

        final Response response = target("domains").path(domainId).path("applications").path(clientId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Application application = readEntity(response, Application.class);
        assertEquals(domainId, application.getDomain());
        assertEquals(clientId, application.getId());
    }

    @Test
    public void shouldGetApplication_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(applicationService).findById(clientId);

        final Response response = target("domains").path(domainId).path("applications").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }


    @Test
    public void shouldGetClient_domainNotFound() {
        final String domainId = "domain-id";
        final String clientId = "client-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("applications").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetApplication_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String applicationId = "application-id";
        final Application mockClient = new Application();
        mockClient.setId(applicationId);
        mockClient.setName("client-name");
        mockClient.setDomain("wrong-domain");

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockClient)).when(applicationService).findById(applicationId);

        final Response response = target("domains").path(domainId).path("applications").path(applicationId).request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Application mockClient = new Application();
        mockClient.setId(clientId);
        mockClient.setName("client-name");
        mockClient.setDomain(domainId);

        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), eq(ReferenceType.APPLICATION), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockClient)).when(applicationService).renewClientSecret(eq(domainId), eq(clientId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("applications")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret_appNotFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Application mockClient = new Application();
        mockClient.setId(clientId);
        mockClient.setName("client-name");
        mockClient.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new ApplicationNotFoundException(clientId))).when(applicationService).renewClientSecret(eq(domainId), eq(clientId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("applications")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
